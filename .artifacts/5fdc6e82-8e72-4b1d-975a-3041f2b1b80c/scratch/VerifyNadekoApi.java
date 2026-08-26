import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyNadekoApi {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String inst = "https://inv.nadeko.net";
        try {
            System.out.println("Querying Nadeko: " + inst);
            URL url = new URL(inst + "/api/v1/videos/" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
                in.close();
                String json = sb.toString();
                
                if (json.contains("adaptiveFormats")) {
                    System.out.println("✅ Found formats.");
                    int urlIdx = json.indexOf("\"url\":\"https://");
                    while (urlIdx != -1) {
                        int end = json.indexOf("\"", urlIdx + 7);
                        String aUrl = json.substring(urlIdx + 7, end);
                        if (aUrl.contains("googlevideo.com")) {
                            System.out.println("Testing stream...");
                            HttpURLConnection test = (HttpURLConnection) new URL(aUrl).openConnection();
                            test.setRequestMethod("GET");
                            test.setRequestProperty("Range", "bytes=0-1024");
                            if (test.getResponseCode() == 200 || test.getResponseCode() == 206) {
                                System.out.println("⭐⭐⭐ SUCCESS! NADEKO API WORKS! ⭐⭐⭐");
                                return;
                            }
                        }
                        urlIdx = json.indexOf("\"url\":\"https://", end);
                    }
                }
            } else {
                System.out.println("HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
