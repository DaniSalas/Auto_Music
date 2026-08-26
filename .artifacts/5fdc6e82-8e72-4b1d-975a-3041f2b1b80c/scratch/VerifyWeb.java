import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyWeb {
    public static void main(String[] args) {
        String videoId = "jNQXAC9IVRw";
        try {
            System.out.println("Step 1: Fetch Visitor Data");
            String visitor = null;
            HttpURLConnection vConn = (HttpURLConnection) new URL("https://www.youtube.com/").openConnection();
            vConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            if (vConn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(vConn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("VISITOR_DATA\":\"")) {
                        int start = line.indexOf("VISITOR_DATA\":\"") + 15;
                        visitor = line.substring(start, line.indexOf("\"", start));
                        break;
                    }
                }
            }
            System.out.println("Visitor: " + visitor);

            System.out.println("Step 2: Player Request (WEB)");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setDoOutput(true);
            
            String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20240821.01.00\",\"hl\":\"en\",\"gl\":\"US\",\"visitorData\":\"" + visitor + "\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                if (response.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ WEB Client Status OK!");
                    if (response.contains("signatureCipher")) {
                        System.out.println("🔒 Ciphered.");
                    } else if (response.contains("\"url\":\"https://")) {
                        System.out.println("🔓 Direct URL found!");
                    }
                } else {
                    System.out.println("❌ WEB Client Failed.");
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
