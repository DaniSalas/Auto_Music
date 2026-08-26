import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyWebRemixBypass {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String visitor = "CgtzZk1RM0ZMdmlxZyjEobfUBjIoCgJFUxIiEh4SHAsMDg8QERITFBUWFxgZGhscHR4fICEiIyQlJicgaWLgAgrdAjE3LllURT1VTHJkSjI0RXFpYjRIMVhDUm02RDRwQUhhSHM3aFVYQmJwQWF3cWxSaFNPSHdjR2lLaDFjQndVX25QRHRQTzVfMlNOcFEzbWw0R3ZlOHkyRms3ZGZWQ0Z0TnlyLTlTdWFQeTBOcnd1V09lN3V3SVRKVms2eTJFSE1rOVhQbFRCREV4aVR5TWMtY1VBQV9NUG1Na2ZlV0RtNG9wbEpGMmRyc2dwS2hkSUxWY09nak0xOTJEbjJKamRZYnZBbkdXQzFvZXlqNUlMelJ6c1laWGRZTTh5b09iSWhqVm5FekNKeEpUcmZ2by1rR25EWmtXa2xxNVAzMUlYRzFHbThrcUdGUm1zRmVpMklYZmh2ZFplYmFaZG1qUjN2Q1dHY1BWWjRYbG9RT2FYUjRWRzA2OVVaalc3MVlpQVFNclhKYUFqTTFrejRCVmVaVGxQN2RKUnVwdkpuT3c%3D";
        try {
            System.out.println("Testing WEB_REMIX bypass for: " + videoId);
            URL url = new URL("https://music.youtube.com/youtubei/v1/player?key=AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("X-Goog-Api-Format-Version", "1");
            conn.setRequestProperty("X-Goog-Visitor-Id", visitor);
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240522.01.00\",\"hl\":\"en\",\"gl\":\"US\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = in.readLine();
                System.out.println("✅ OK! Status: " + line.contains("\"status\":\"OK\""));
            } else {
                 System.out.println("❌ " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
