import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter server IP address (default localhost): ");
        String serverIp = scanner.nextLine();
        if (serverIp.isEmpty()) {
            serverIp = "localhost";
        }

        System.out.print("Enter server port number (default 9025): ");
        String portInput = scanner.nextLine();
        int port = portInput.isEmpty() ? 9025 : Integer.parseInt(portInput);

        try (Socket socket = new Socket(serverIp, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to server on " + serverIp + ":" + port);

        // Thread to handle incoming messages from the server
		new Thread(() -> {
			try {
				String serverMessage;
				while ((serverMessage = in.readLine()) != null) {
					if ("Exiting the server. Goodbye!".equals(serverMessage.trim())) {
					break; // Break the loop to close client resources
					}
					System.out.println(serverMessage);
				}
			} catch (IOException e) {
				System.err.println("Error reading from server: " + e.getMessage());
				e.printStackTrace();
			} finally {
				try {
					if (out != null) out.close();
					if (in != null) in.close();
					if (socket != null) socket.close();
				} catch (IOException e) {
					System.err.println("Error closing resources: " + e.getMessage());
					e.printStackTrace();
				}		
				System.exit(0); // Exit the program
			}
		}).start();

            // Handling user input and sending to the server
            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                
            }
        } catch (UnknownHostException ex) {
            System.err.println("Host unknown: " + ex.getMessage());
            ex.printStackTrace();
        } catch (IOException ex) {
            System.err.println("I/O error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
