
import com.moandjiezana.toml.Toml;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;

public class ZephyrUploader {

    public static void uploadResults(String xmlFilePath, String configPath) throws IOException {
        Toml toml = new Toml().read(new File(configPath));

        String uploadUrl = toml.getString("zephyr.upload_url");
        String authToken = toml.getString("zephyr.auth_token");

        Map<String, Object> jobDetail = toml.getTable("automationJobDetail").toMap();
        String boundary = "===" + System.currentTimeMillis() + "===";
        String LINE_FEED = "\r\n";
        String charset = "UTF-8";

        HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Authorization", "Bearer " + authToken);

        OutputStream outputStream = connection.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);

        // Attach file
        File uploadFile = new File(xmlFilePath);
        String fileName = uploadFile.getName();
        writer.append("--").append(boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"fileName\"; filename=\"").append(fileName).append("\"").append(LINE_FEED);
        writer.append("Content-Type: text/xml").append(LINE_FEED);
        writer.append(LINE_FEED).flush();
        Files.copy(uploadFile.toPath(), outputStream);
        outputStream.flush();
        writer.append(LINE_FEED).flush();

        // Build JSON from jobDetail map
        StringBuilder jobJson = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : jobDetail.entrySet()) {
            jobJson.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                jobJson.append("\"").append(entry.getValue()).append("\"");
            } else {
                jobJson.append(entry.getValue());
            }
            jobJson.append(",");
        }
        if (jobJson.charAt(jobJson.length() - 1) == ',') {
            jobJson.deleteCharAt(jobJson.length() - 1); // Remove trailing comma
        }
        jobJson.append("}");

        writer.append("--").append(boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"automationJobDetail\"").append(LINE_FEED);
        writer.append("Content-Type: application/json; charset=").append(charset).append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.append(jobJson.toString()).append(LINE_FEED);
        writer.append("--").append(boundary).append("--").append(LINE_FEED);
        writer.flush();
        writer.close();

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            System.out.println("Upload successful. Server response: " + response.toString());
        } else {
            System.out.println("Upload failed. Server returned status: " + status);
        }

        connection.disconnect();
    }
}
