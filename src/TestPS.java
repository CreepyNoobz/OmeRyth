import java.io.*;

public class TestPS {
    public static void main(String[] args) throws Exception {
        String filter = "Fichiers|*.*";
        String title = "Test Title";
        String script = "Add-Type -AssemblyName PresentationFramework\n" +
               "$dlg = New-Object Microsoft.Win32.SaveFileDialog\n" +
               "$dlg.Title = '" + title.replace("'", "''") + "'\n" +
               "$dlg.Filter = '" + filter.replace("'", "''") + "'\n" +
               "$res = $dlg.ShowDialog()\n" +
               "if ($res -eq $true) { Write-Output $dlg.FileName }";
               
        String b64 = java.util.Base64.getEncoder().encodeToString(script.getBytes("UTF-16LE"));
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", b64);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        p.waitFor();
    }
}
