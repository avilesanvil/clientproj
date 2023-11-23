/*

	Richard Delforge, Cameron Devenport, Johnny Do
	Chat Room Project
	COSC 4333 - Distributed Systems
	Dr. Sun
	11/27/2023
	
*/

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int DEFAULT_PORT = 9025;
    private static final int MAX_PORT = 65535;
    private static Map<String, ChatRoomHandler> chatRooms = new ConcurrentHashMap<>();


    public static void main(String[] args) {

        // Declare and initialize ipAddress and port here
        String ipAddress = "0.0.0.0"; // Default IP address
        int port = DEFAULT_PORT;        // Default port

        // Update values based on command-line arguments
        if (args.length > 0) {
            ipAddress = args[0]; // Get IP address from command-line argument
            if (args.length > 1) {
                port = Integer.parseInt(args[1]); // Optional: Get port from command-line argument
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(10);

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(ipAddress))) {
            // Print the IP address and port to the console (Displayed IP number is changed for demonstration purposes)
            System.out.println("Server is listening on IP: 192.168.x.x Port: " + serverSocket.getLocalPort());

            while (true) {
                Socket clientSocket = serverSocket.accept();
				System.out.println("Client connected from " + clientSocket.getInetAddress().getHostAddress());
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    private static int findAvailablePort(int startPort) {
        while (startPort <= MAX_PORT) {
            try (ServerSocket serverSocket = new ServerSocket(startPort)) {
                return startPort;
            } catch (IOException ignored) {
                startPort++;
            }
        }
        return -1;
    }
	
	private static class ChatRoomHandler implements Runnable {
		private String roomName;
		private Set<PrintWriter> clients = ConcurrentHashMap.newKeySet();

		public ChatRoomHandler(String roomName) {
			this.roomName = roomName;
		}

		public void addClient(PrintWriter client) {
			clients.add(client);
		}

		public void removeClient(PrintWriter client) {
			clients.remove(client);
			if (clients.isEmpty()) {
				chatRooms.remove(roomName);
			}
		}

		public void broadcastMessage(String message) {
			for (PrintWriter client : clients) {
				client.println(message);
			}
		}
		
		public int getNumberOfClients() {
			return clients.size();
		}


		public void run() {
			// Here you can add any continuous logic for the chat room, if needed.
		}
	}


    private static class ClientHandler implements Runnable {
        private Socket clientSocket; 
        private PrintWriter out; 
        private BufferedReader in; 
        private String currentRoom; 
        private String clientName; 

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                out.println("Enter your name:");
                clientName = in.readLine();
                out.println("Welcome " + clientName + "! You can join a room with JOIN <room_name>, leave with LEAVE, list existing chatrooms with LISTROOMS, or send messages.");

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.startsWith("JOIN ")) {
                        joinChatRoom(inputLine.substring(5));
                    } else if ("LEAVE".equals(inputLine)) {
                        leaveChatRoom();
                    } else if ("LISTROOMS".equals(inputLine)) {
                        listChatRooms();
                    } else if ("EXIT".equals(inputLine)) {
						if (currentRoom != null) {
						leaveChatRoom();  // Leave the room if the client is in one
					}
					out.println("Exiting the server. Goodbye!");
					closeResources();
					break; // Break out of the loop to end this handler thread
					} else {
                        sendMessageToChatRoom(clientName + ": " + inputLine, this.out);
					}
				}
            } catch (IOException ex) {
                System.out.println("Server exception: " + ex.getMessage());
                ex.printStackTrace();
				
			// The finally block is now only relevant if the while loop exits unexpectedly
			} finally {
				if (currentRoom != null) {
					leaveChatRoom();  // Ensure to leave the room if still connected
				}
				out.println("SERVER_CLOSE_CONNECTION"); // Send disconnection message in the finally block as well
				closeResources();  // Close resources when exiting the loop
			}
        }

        private void joinChatRoom(String roomName) {
			boolean isNewRoom = !chatRooms.containsKey(roomName); // Check if the room is new
			leaveChatRoom(); // Leave the current room if any
			ChatRoomHandler roomHandler = chatRooms.computeIfAbsent(roomName, k -> new ChatRoomHandler(roomName));
			roomHandler.addClient(out);
			currentRoom = roomName;
			if(isNewRoom) {
				Thread newRoomThread = new Thread(roomHandler);
				newRoomThread.start(); // Start the chat room handler thread if it is a new room
				System.out.println("New thread created for chat room: " + roomName + ", Thread ID: " + newRoomThread);
			}
			System.out.println(clientName + " has entered chat room: " + roomName);

			// Send a message to the user indicating successful joining of the room
			out.println("You have successfully joined the room: " + roomName);
		}


		private void leaveChatRoom() {
			if (currentRoom != null) {
				ChatRoomHandler roomHandler = chatRooms.get(currentRoom);
				if (roomHandler != null) {
					roomHandler.removeClient(out);
				}
				out.println("Left room: " + currentRoom);
				System.out.println(clientName + " has left chat room: " + currentRoom);
				currentRoom = null;
			}
		}


        private void listChatRooms() {
			System.out.println("Listing chat rooms..."); // Debug statement
			System.out.println("Available chat rooms:");
			for (Map.Entry<String, ChatRoomHandler> entry : chatRooms.entrySet()) {
				// Debug statement to check each entry
				System.out.println("Checking room: " + entry.getKey());
				int numberOfUsers = entry.getValue().getNumberOfClients(); 
				out.println(" - " + entry.getKey() + " (" + numberOfUsers + " users)");
			}
		}



        private void sendMessageToChatRoom(String message, PrintWriter senderOut) {
			String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
			String formattedMessage = "\n" + "[" + time + "] " + message.trim();
			if (currentRoom != null) {
				ChatRoomHandler roomHandler = chatRooms.get(currentRoom);
			if (roomHandler != null) {
            roomHandler.broadcastMessage(formattedMessage);
				}
			}				
		}


        private void closeResources() {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
