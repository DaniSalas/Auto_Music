import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyFresh {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String visitor = "CgtzZk1RM0ZMdmlxZyjEobfUBjIoCgJFUxIiEh4SHAsMDg8QERITFBUWFxgZGhscHR4fICEiIyQlJicgaWLgAgrdAjE3LllURT1VTHJkSjI0RXFpYjRIMVhDUm02RDRwQUhhSHM3aFVYQmJwQWF3cWxSaFNPSHdjR2lLaDFjQndVX25QRHRQTzVfMlNOcFEzbWw0R3ZlOHkyRms3ZGZWQ0Z0TnlyLTlTdWFQeTBOcnd1V09lN3V3SVRKVms2eTJFSE1rOVhQbFRCREV4aVR5TWMtY1VBQV9NUG1Na2ZlV0RtNG9wbEpGMmRyc2dwS2hkSUxWY09nak0xOTJEbjJKamRZYnZBbkdXQzFvZXlqNUlMelJ6c1laWGRZTTh5b09iSWhqVm5FekNKeEpUcmZ2by1rR25EWmtXa2xxNVAzMUlYRzFHbThrcUdGUm1zRmVpMklYZmh2ZFplYmFaZG1qUjN2Q1dHY1BWWjRYbG9RT2FYUjRWRzA2OVVaalc3MVlpQVFNclhKYUFqTTFrejRCVmVaVGxQN2RKUnVwdkpuT3c%3D";
        try {
            System.out.println("Testing ANDROID_MUSIC with Fresh Visitor Data...");
            URL url = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.apps.youtube.music/7.01.52 (Linux; U; Android 14)");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_MUSIC\",\"clientVersion\":\"7.01.52\",\"hl\":\"en\",\"gl\":\"US\",\"visitorData\":\"" + visitor + "\"}},\"videoId\":\"" + videoId + "\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":20684}}}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                System.out.println("✅ Status: " + json.contains("\"status\":\"OK\""));
                if (json.contains("\"url\":\"https://")) System.out.println("⭐⭐⭐ SUCCESS! ⭐⭐⭐");
            } else {
                 System.out.println("❌ HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
