import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Sistem pakar sederhana: Forward Chaining dengan basis aturan JSON.
 * Mesin inferensi dan pembaca JSON ditulis sendiri (tanpa library luar).
 * Jalankan: java SistemPakar.java [file_aturan.json]
 */
public class SistemPakar {

    record Aturan(String id, List<String> jika, String mode, String maka, String sumber) {
        boolean terpenuhi(Set<String> memori) {
            if (mode.equals("dan")) {
                for (String j : jika) if (!memori.contains(j)) return false;
                return true;
            }
            for (String j : jika) if (memori.contains(j)) return true;
            return false;
        }
    }

    // ---------- Pembaca JSON mini ----------
    static class MiniJson {
        private final String s;
        private int i = 0;
        MiniJson(String s) { this.s = s; }

        static Object parse(String teks) {
            MiniJson p = new MiniJson(teks);
            Object v = p.nilai();
            p.spasi();
            if (p.i != teks.length()) throw p.salah("ada teks sisa");
            return v;
        }
        RuntimeException salah(String m) { return new RuntimeException("JSON salah di posisi " + i + ": " + m); }
        void spasi() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        void harus(char c) {
            spasi();
            if (i >= s.length() || s.charAt(i) != c) throw salah("seharusnya '" + c + "'");
            i++;
        }
        Object nilai() {
            spasi();
            if (i >= s.length()) throw salah("data habis");
            char c = s.charAt(i);
            if (c == '{') return objek();
            if (c == '[') return larik();
            if (c == '"') return teks();
            throw salah("karakter tidak dikenal '" + c + "'");
        }
        Map<String, Object> objek() {
            Map<String, Object> m = new LinkedHashMap<>();
            harus('{');
            spasi();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                spasi();
                String k = teks();
                harus(':');
                m.put(k, nilai());
                spasi();
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw salah("seharusnya ',' atau '}'");
            }
        }
        List<Object> larik() {
            List<Object> l = new ArrayList<>();
            harus('[');
            spasi();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(nilai());
                spasi();
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw salah("seharusnya ',' atau ']'");
            }
        }
        String teks() {
            harus('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
            return sb.toString();
        }
    }

    // ---------- Basis pengetahuan ----------
    static String judul, batas;
    static Map<String, String> sumber = new LinkedHashMap<>();
    static List<Aturan> aturan = new ArrayList<>();

    @SuppressWarnings("unchecked")
    static void muat(String path) throws Exception {
        String isi = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(isi);
        judul = (String) root.get("judul");
        batas = (String) root.get("batas");
        for (var e : ((Map<String, Object>) root.get("sumber")).entrySet())
            sumber.put(e.getKey(), (String) e.getValue());
        for (Object o : (List<Object>) root.get("aturan")) {
            Map<String, Object> a = (Map<String, Object>) o;
            List<String> jika = new ArrayList<>();
            for (Object j : (List<Object>) a.get("jika")) jika.add((String) j);
            String mode = (String) a.get("mode");
            if (!mode.equals("dan") && !mode.equals("atau"))
                throw new RuntimeException("mode aturan " + a.get("id") + " harus 'dan' atau 'atau'");
            Aturan r = new Aturan((String) a.get("id"), jika, mode, (String) a.get("maka"), (String) a.get("sumber"));
            for (String sid : r.sumber().split(",\\s*"))
                if (!sumber.containsKey(sid))
                    throw new RuntimeException("aturan " + r.id() + " menunjuk sumber tak dikenal: " + sid);
            aturan.add(r);
        }
    }

    // ---------- Forward chaining ----------
    static Set<String> memori = new LinkedHashSet<>();
    static Map<String, Aturan> asal = new LinkedHashMap<>(); // fakta turunan -> aturan penghasil

    static void forwardChaining() {
        int siklus = 0;
        while (true) {
            Aturan pilih = null;
            for (Aturan r : aturan) {
                if (!memori.contains(r.maka()) && r.terpenuhi(memori)) { pilih = r; break; }
            }
            siklus++;
            if (pilih == null) {
                System.out.println("Siklus " + siklus + ": tidak ada aturan baru, berhenti");
                return;
            }
            memori.add(pilih.maka());
            asal.put(pilih.maka(), pilih);
            System.out.println("Siklus " + siklus + ": " + pilih.id() + " -> " + pilih.maka());
        }
    }

    static void jelaskan(String fakta, int tingkat, Set<String> sudah) {
        Aturan r = asal.get(fakta);
        if (r == null) return;
        List<String> dipakai = new ArrayList<>();
        for (String j : r.jika()) if (memori.contains(j)) dipakai.add(j);
        System.out.println("  ".repeat(tingkat) + fakta + " <- " + r.id() + " (" + String.join(", ", dipakai) + ")");
        if (!sudah.add(fakta)) return;
        for (String j : dipakai) jelaskan(j, tingkat + 1, sudah);
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        muat(args.length > 0 ? args[0] : "kb_banjir.json");

        // fakta dasar = syarat yang tidak pernah menjadi hasil aturan
        Set<String> hasil = new HashSet<>();
        for (Aturan r : aturan) hasil.add(r.maka());
        Set<String> dasar = new LinkedHashSet<>();
        for (Aturan r : aturan) for (String j : r.jika()) if (!hasil.contains(j)) dasar.add(j);

        System.out.println(judul);
        System.out.println("Jawab y/t.");
        Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
        boolean interaktif = System.console() != null;
        int no = 1;
        for (String f : dasar) {
            System.out.print(no++ + ". Apakah " + f + "? (y/t) > ");
            String j = in.hasNextLine() ? in.nextLine().trim().toLowerCase() : "t";
            if (!interaktif) System.out.println(j);
            if (j.equals("y") || j.equals("ya")) memori.add(f);
        }

        System.out.println();
        System.out.println("Fakta awal: " + (memori.isEmpty() ? "(tidak ada)" : String.join(", ", memori)));
        System.out.println();
        Set<String> faktaAwal = new LinkedHashSet<>(memori);
        forwardChaining();

        // kesimpulan akhir = fakta turunan yang tidak dipakai sebagai syarat oleh aturan yang dijalankan
        Set<String> dipakaiSyarat = new HashSet<>();
        for (Aturan r : asal.values())
            for (String j : r.jika()) if (memori.contains(j)) dipakaiSyarat.add(j);
        List<String> akhir = new ArrayList<>();
        for (String f : asal.keySet()) if (!dipakaiSyarat.contains(f)) akhir.add(f);

        System.out.println();
        if (akhir.isEmpty()) {
            System.out.println("Kesimpulan: tidak ada kesimpulan dari fakta yang diberikan");
        } else {
            System.out.println("Kesimpulan: " + String.join("; ", akhir));
            System.out.println("Alasan:");
            Set<String> sudah = new HashSet<>();
            for (String k : akhir) jelaskan(k, 1, sudah);
            Set<String> sumberDipakai = new TreeSet<>();
            for (String f : asal.keySet()) sumberDipakai.addAll(Arrays.asList(asal.get(f).sumber().split(",\\s*")));
            System.out.println("Sumber yang dipakai: " + String.join(", ", sumberDipakai));
        }
        System.out.println();
        System.out.println("Catatan: " + batas);
    }
}
