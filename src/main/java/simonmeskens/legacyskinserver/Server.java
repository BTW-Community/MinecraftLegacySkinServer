package simonmeskens.legacyskinserver;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import net.freeutils.httpserver.HTTPServer;
import net.freeutils.httpserver.HTTPServer.ContextHandler;
import net.freeutils.httpserver.HTTPServer.Request;
import net.freeutils.httpserver.HTTPServer.Response;
import net.freeutils.httpserver.HTTPServer.VirtualHost;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class Server implements ContextHandler {
    public static void main(String[] args) throws IOException {
        int port = 5444;

        try {
            int newPort = Integer.parseInt(args[0]);
            if (newPort > 1024) {
                port = newPort;
            }
        } catch (Exception e) {
        }

        HTTPServer server = new HTTPServer(port);

        VirtualHost host = server.getVirtualHost(null);
        host.addContext("/MinecraftSkins", new Server());

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGui();
                System.out.println("Starting skin server...");
            }
        });

        server.start();
    }

    private static void createAndShowGui() {
        JFrame frame = new JFrame("Console");
        JTextArea textArea = new JTextArea(15, 30);

        TextAreaOutputStream taOutputStream = new TextAreaOutputStream(
                textArea);
        System.setOut(new PrintStream(taOutputStream));

        JScrollPane scrollPane = new JScrollPane(textArea,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(scrollPane);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private HashMap<String, String> uuids = new HashMap<String, String>();
    private HashMap<String, ByteArrayOutputStream> textures = new HashMap<String, ByteArrayOutputStream>();

    public int serve(Request req, Response resp) throws IOException {
        String userName = new File(req.getPath(), "").getName().replaceFirst("[.][^.]+$", "");
        try {
            InputStream stream = getSkin(userName);
            resp.sendHeaders(200, stream.available(),
                    System.currentTimeMillis(), null, "image/png", null);
            resp.sendBody(stream, stream.available(), null);
            System.out.println("Served skin for \"" + userName + "\"");
        } catch (Exception e) {
            resp.send(500, "Failed serving " + req.getPath() + ": " + e.getMessage());
            System.out.println("Failed to serve skin for \"" + userName + "\": " + e.getMessage());
        }

        return 0;
    }

    public InputStream getSkin(String userName) throws Exception {
        if (!uuids.containsKey(userName)) {
            System.out.println("Fetching UUID for \"" + userName + "\"...");
            uuids.put(userName, fetchUUID(userName));
        }

        if (!textures.containsKey(userName)) {
            System.out.println("Fetching texture for \"" + userName + "\"...");
            textures.put(userName, fetchTexture(uuids.get(userName)));
        }

        return new ByteArrayInputStream(textures.get(userName).toByteArray());
    }

    public String fetchUUID(String userName) throws Exception {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + userName);

        JsonObject response = null;
        try {
            response = JsonParser.object().from(url);
        }
        catch(Exception e) {
            throw new Exception("Failed to get UUID");
        }

        if (response.getString("name").equals(userName)) {
            return response.getString("id");
        }

        throw new Exception("Error parsing UUID request");
    }

    public ByteArrayOutputStream fetchTexture(String uuid) throws Exception {
        URL url = new URL("https://crafatar.com/skins/" + uuid);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("GET");
        conn.connect();

        InputStream in = conn.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        conn.disconnect();

        return buffer;
    }
}
