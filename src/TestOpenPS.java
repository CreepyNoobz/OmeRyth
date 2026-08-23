import java.io.*;

public class TestOpenPS {
    public static void main(String[] args) throws Exception {
        String title = "Test Open";
        String[] extensions = {"mp4", "mkv", "avi"};
        
        StringBuilder filter = new StringBuilder("Fichiers supportés|");
        if (extensions != null && extensions.length > 0) {
            for (int i = 0; i < extensions.length; i++) {
                if (i > 0) filter.append(";");
                filter.append("*.").append(extensions[i]);
            }
        } else {
            filter.append("*.*");
        }
        filter.append("|Tous les fichiers|*.*");

        String script = "Add-Type -AssemblyName PresentationFramework\n" +
               "$dlg = New-Object Microsoft.Win32.OpenFileDialog\n" +
               "$dlg.Title = '" + title.replace("'", "''") + "'\n" +
               "$dlg.Filter = '" + filter.toString().replace("'", "''") + "'\n" +
               "$res = $dlg.ShowDialog()\n" +
               "if ($res -eq $true) { Write-Output $dlg.FileName }";
               
        System.out.println("Script:");
        System.out.println(script);
        
        String b64 = java.util.Base64.getEncoder().encodeToString(script.getBytes("UTF-16LE"));
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Sta", "-EncodedCommand", b64);
        pb.redirectErrorStream(false);
        Process p = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        String result = null;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty() && !line.startsWith("#< CLIXML")) {
                result = line.trim();
                System.out.println("Line: " + line);
            }
        }
        p.waitFor();
        System.out.println("Final Result: " + result);
    }
}
