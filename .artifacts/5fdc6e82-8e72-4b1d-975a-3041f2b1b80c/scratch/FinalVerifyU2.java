import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FinalVerifyU2 {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            // 1. Get Fresh Visitor Data
            System.out.println("Fetching fresh Visitor Data...");
            String visitorData = null;
            URL homeUrl = new URL("https://www.youtube.com/");
            HttpURLConnection homeConn = (HttpURLConnection) homeUrl.openConnection();
            homeConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            if (homeConn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(homeConn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("VISITOR_DATA\":\"")) {
                        int start = line.indexOf("VISITOR_DATA\":\"") + 15;
                        int end = line.indexOf("\"", start);
                        visitorData = line.substring(start, end);
                        break;
                    }
                }
            }
            if (visitorData == null) visitorData = "CgthdE0yLVF3TlZCRSj287bUBjIoCgJFUxIiEh4SHAsMDg8QERITFBUWFxgZGhscHR4fICEiIyQlJicgRWLgAgrdAjE3LllURT1VTHJkSjI0RXFpYjRIMVhDUm02RDRwQUhhSHM3aFVYQmJwQWF3cWxSaFNPSHdjR2lLaDFjQndVX25QRHRQTzVfMlNOcFEzbWw0R3ZlOHkyRms3ZGZWQ0Z0TnlyLTlTdWFQeTBOcnd1V09lN3V3SVRKVms2eTJFSE1rOVhQbFRCREV4aVR5TWMtY1VBQV9NUG1Na2ZlV0RtNG9wbEpGMmRyc2dwS2hkSUxWY09nak0xOTJEbjJKamRZYnZBbkdXQzFvZXlqNUlMelJ6c1laWGRZTTh5b09iSWhqVm5FekNKeEpUcmZ2by1rR25EWmtXa2xxNVAzMUlYRzFHbThrcUdGUm1zRmVpMklYZmh2ZFplYmFaZG1qUjN2Q1dHY1BWWjRYbG9RT2FYUjRWRzA2OVVaalc3MVlpQVFNclhKYUFqTTFrejRCVmVaVGxQN2RKUnVwdkpuT3c%3D";
            System.out.println("Using Visitor Data: " + visitorData.substring(0, 30) + "...");

            // 2. Try ANDROID_VR client
            System.out.println("Requesting player info via ANDROID_VR...");
            URL playerUrl = new URL("https://www.youtube.com/youtubei/v1/player?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
            HttpURLConnection conn = (HttpURLConnection) playerUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3)");
            conn.setDoOutput(true);

            String payload = "{\"context\":{\"client\":{\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.61.48\",\"hl\":\"en\",\"gl\":\"US\",\"visitorData\":\"" + visitorData + "\"}},\"videoId\":\"" + videoId + "\"}";
            try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                String json = sb.toString();
                
                if (json.contains("\"status\":\"OK\"")) {
                    System.out.println("✅ Status: OK");
                    if (json.contains("\"url\":\"https://")) {
                        int uStart = json.indexOf("\"url\":\"https://") + 7;
                        int uEnd = json.indexOf("\"", uStart);
                        String streamUrl = json.substring(uStart, uEnd).replace("\\u0026", "&");
                        System.out.println("✅ FOUND DIRECT URL!");
                        verifyStream(streamUrl, "com.google.android.apps.youtube.vr.oculus/1.61.48");
                        return;
                    } else if (json.contains("signatureCipher")) {
                        System.out.println("🔒 CIPHERED. Fallback to Invidious Proxy.");
                    }
                } else {
                    System.out.println("❌ Status: FAIL");
                }
            } else {
                System.out.println("❌ HTTP " + conn.getResponseCode());
            }

            // 3. Try Invidious Direct GET (Not latest_version, but full video info)
            System.out.println("Requesting Invidious Video Info (ProjectSegfault)...");
            URL invUrl = new URL("https://invidious.projectsegfau.lt/api/v1/videos/" + videoId);
            HttpURLConnection invConn = (HttpURLConnection) invUrl.openConnection();
            invConn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (invConn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(invConn.getInputStream()));
                String line = in.readLine();
                if (line != null && line.contains("audioStreams")) {
                    int sStart = line.indexOf("\"url\":\"https://") + 7;
                    int sEnd = line.indexOf("\"", sStart);
                    String directUrl = line.substring(sStart, sEnd);
                    System.out.println("✅ FOUND INVIDIOUS STREAM!");
                    verifyStream(directUrl, "Mozilla/5.0");
                    return;
                }
            } else {
                System.out.println("❌ Invidious API " + invConn.getResponseCode());
            }

        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("❌ ALL STRATEGIES FAILED.");
    }

    private static void verifyStream(String url, String ua) {
        try {
            System.out.println("Verifying stream playback...");
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", ua);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            int code = conn.getResponseCode();
            System.out.println("Playback Response: " + code);
            if (code == 200 || code == 206) {
                System.out.println("⭐⭐⭐ SUCCESS! URL IS ACTIVE! ⭐⭐⭐");
            } else {
                System.out.println("❌ Stream verification failed with code " + code);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
