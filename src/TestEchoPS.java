import java.io.*;

public class TestEchoPS {
    public static void main(String[] args) throws Exception {
        String script = "Write-Output 'C:\\Users\\kilya\\Desktop\\OmeRyth\\test.mp4'";
               
        String b64 = java.util.Base64.getEncoder().encodeToString(script.getBytes("UTF-16LE"));
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Sta", "-EncodedCommand", b64);
        pb.redirectErrorStream(false);
        Process p = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        String result = null;
        while ((line = reader.readLine()) != null) {
            System.out.println("LINE: " + line);
            if (!line.trim().isEmpty() && !line.startsWith("#< CLIXML")) {
                result = line.trim();
            }
        }
        p.waitFor();
        System.out.println("RESULT: " + result);
    }
}
