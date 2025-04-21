package utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import Model.Message;
//Use json Lib
public class AIIntegration {
    private static final String AI_API_URL = "http://localhost:11434/api/generate";
    private static final String DEFAULT_MODEL = "llama3";

    public static String performQuery(String clientInput, List<Message> context) {
        try {
            StringBuilder promptBuilder = new StringBuilder(clientInput);
            promptBuilder.append("\n\nContext from previous messages:\n");
            for (Message m : context) {
                promptBuilder.append("- ").append(m.getContent()).append("\n");
            }

            String payload = createJsonPayload(DEFAULT_MODEL, promptBuilder.toString(), false);

            URL url = new URI(AI_API_URL).toURL();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Send the JSON request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Get the response
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                StringBuilder responseBuilder = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    responseBuilder.append(responseLine.trim());
                }

                String responseJson = responseBuilder.toString();
                String response = extractResponseFromJson(responseJson);

                return response;

            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Sorry, I encountered an error: " + e.getMessage();
        }
    }

    private static String createJsonPayload(String model, String prompt, boolean stream) {
        return "{" +
                "\"model\": \"" + model + "\"," +
                "\"prompt\": \"" + prompt.replace("\"", "\\\"") + "\"," + 
                "\"stream\": " + stream +
                "}";
    }

    private static String extractResponseFromJson(String json) {
        String responseKey = "\"response\":";
        int startIndex = json.indexOf(responseKey) + responseKey.length();
        int endIndex = json.indexOf("\"", startIndex + 1);
        return json.substring(startIndex + 1, endIndex); 
    }
}
