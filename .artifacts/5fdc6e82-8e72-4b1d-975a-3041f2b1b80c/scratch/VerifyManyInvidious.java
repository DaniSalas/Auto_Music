import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyManyInvidious {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        String[] instances = {
            "https://invidious.jing.rocks",
            "https://iv.ggtyler.dev",
            "https://invidious.projectsegfau.lt",
            "https://inv.nadeko.net",
            "https://invidious.tiekoetter.com"
        };
        for (String inst : instances) {
            try {
                System.out.print("Testing " + inst + "... ");
                String target = inst + "/latest_version?id=" + videoId + "&itag=140&local=true";
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range", "bytes=0-1024");
                conn.setConnectTimeout(4000);
                int code = conn.getResponseCode();
                System.out.println(code);
                if (code == 200 || code == 206) {
                    System.out.println("⭐⭐⭐ SUCCESS! USE: " + target + " ⭐⭐⭐");
                    return;
                }
            } catch (Exception e) { System.out.println("Error"); }
        }
    }
}
