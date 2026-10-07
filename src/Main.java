import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: github-activity <username>");
            return;
        }

        String username = args[0];
        String url = "https://api.github.com/users/" + username + "/events";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "github-activity-cli")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            System.out.println("Network error: " + e.getMessage());
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Request was interrupted.");
            return;
        }

        switch (response.statusCode()) {
            case 200:
                printActivity(response.body());
                break;
            case 404:
                System.out.println("User not found.");
                break;
            case 403:
            case 429:
                System.out.println("Rate limit exceeded. Please try again later.");
                break;
            default:
                System.out.println("Failed to fetch GitHub activity (HTTP " + response.statusCode() + ").");
        }
    }

    private static void printActivity(String json) {
        JsonArray events = JsonParser.parseString(json).getAsJsonArray();

        if (events.size() == 0) {
            System.out.println("No recent activity.");
            return;
        }

        for (JsonElement element : events) {
            System.out.println("- " + describe(element.getAsJsonObject()));
        }
    }

    private static String describe(JsonObject event) {
        String type = str(event, "type");
        String repo = str(event.getAsJsonObject("repo"), "name");
        JsonObject payload = event.getAsJsonObject("payload");

        switch (type) {
            case "PushEvent": {
                int commits = countCommits(payload);
                return "Pushed " + commits + (commits == 1 ? " commit" : " commits") + " to " + repo;
            }
            case "IssuesEvent": {
                String action = str(payload, "action");
                if (action.equals("opened")) {
                    return "Opened a new issue in " + repo;
                }
                return capitalize(action) + " an issue in " + repo;
            }
            case "WatchEvent":
                return "Starred " + repo;
            case "ForkEvent":
                return "Forked " + repo;
            case "CreateEvent": {
                String refType = str(payload, "ref_type");
                if (refType.equals("repository")) {
                    return "Created repository " + repo;
                }
                return "Created " + refType + " " + str(payload, "ref") + " in " + repo;
            }
            case "PullRequestEvent": {
                String action = str(payload, "action");
                JsonObject pr = payload.getAsJsonObject("pull_request");
                if (action.equals("closed") && pr != null
                        && pr.has("merged") && pr.get("merged").getAsBoolean()) {
                    action = "merged";
                }
                return capitalize(action) + " a pull request in " + repo;
            }
            default:
                return type.replace("Event", "") + " in " + repo;
        }
    }

    // Số commit: ưu tiên "size", nếu thiếu thì đếm mảng "commits".
    private static int countCommits(JsonObject payload) {
        if (payload.has("size") && !payload.get("size").isJsonNull()) {
            return payload.get("size").getAsInt();
        }
        if (payload.has("commits") && payload.get("commits").isJsonArray()) {
            return payload.getAsJsonArray("commits").size();
        }
        return 0;
    }

    // Lấy string an toàn: thiếu field hoặc null thì trả về chuỗi rỗng.
    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
 
