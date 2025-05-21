import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

public class Server {

    public static void main(String[] args) {
        // Ensure the correct number of arguments are provided
        if (args.length != 1 ) {
            System.out.println("Usage: java Server <port>");
            return;
        }

        // Parse the port number from the command line
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Please provide a valid integer.");
            return;
        }

        try {
            DatagramSocket serverSocket = new DatagramSocket(port);
            System.out.println("Server is listening on port " + port);
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(requestPacket);

                // Receive the request
                String receivedData = new String(requestPacket.getData(), 0, requestPacket.getLength());
                System.out.println("Received data from client: " + receivedData);

                // Grab the file 
                String[] downloadArray = receivedData.split(" ");
                String filename = downloadArray[1];
                String filepath = "C:\\Users\\alexi\\Documents\\University\\2nd Year\\COMPX234\\Assignment 3\\Server_Files\\" + filename; 

                // Check if the file exists
                File file = new File(filepath);
                String encodedString;

                if (file.exists()){
                    // Grab file size 
                    long size = file.length();
                    // byte[] fileBytes = Files.readAllBytes(Paths.get(filepath));
                    // int size = Base64.getEncoder().encodeToString(fileBytes).length();

                    // Pick a random data port that is not in used
                    Random rand = new Random();
                    int dataPort = 5000 + rand.nextInt(1001);

                    // Encode string
                    encodedString = "OK " + filename + " SIZE " + size + " PORT " + dataPort;

                    // Send the response back to the client
                    byte[] sendData = encodedString.getBytes();
                    InetAddress clientAddress = requestPacket.getAddress();
                    int clientPort = requestPacket.getPort();
                    DatagramPacket responsePacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                    serverSocket.send(responsePacket);

                    // Start a new thread for the client
                      Thread clientThread = new Thread(){
                        public void run(){
                            try {
                                // Create and connect the UDP socket
                                DatagramSocket clientSocket = new DatagramSocket(dataPort);

                                // Grab the bytes of the file
                                byte[] fileBytes = new byte[(int)file.length()];
                                try(FileInputStream fis = new FileInputStream(file);){
                                    fis.read(fileBytes);
                                    fis.close();
                                }
                                
                                // Continuous communication
                                while (true){
                                    // Receive the request
                                    DatagramPacket clientPacket = new DatagramPacket(buffer, buffer.length);
                                    clientSocket.receive(clientPacket);
                                    String receivedData = new String(clientPacket.getData(), 0, clientPacket.getLength());
                                    System.out.println("Client request: " + receivedData);
                                    
                                    // Split the request
                                    String[] receivedDataArray = receivedData.split(" ");

                                    // Check what the request is
                                    if (receivedDataArray[2].equals("GET")){
                                        // Check if the file name is correct
                                        if (!receivedDataArray[1].equals(filename)){
                                            System.out.println("Error: invalid file name");
                                        }

                                        // Grab the start and end bytes
                                        int startByte = Integer.parseInt(receivedDataArray[4]);
                                        int endByte = Integer.parseInt(receivedDataArray[6]);

                                        // Grab the file bytes of that range and convert it to string
                                        byte[] rangeBytes = Arrays.copyOfRange(fileBytes, startByte, endByte);
                                        String rangeBytesData = Base64.getEncoder().encodeToString(rangeBytes);

                                        // Send the data to the client
                                        String byteData = "FILE " + filename + " START " + startByte + " END " + endByte + " DATA " + rangeBytesData;
                                        
                                        byte[] sendData = byteData.getBytes();
                                        InetAddress clientAddress = requestPacket.getAddress();
                                        int clientPort = requestPacket.getPort();
                                        DatagramPacket responsePacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                                        serverSocket.send(responsePacket);
                                    }
                                    else if (receivedDataArray[2].equals("CLOSE")){
                                        // Close the file
                                        // Send the data to the client
                                        String closeString = "FILE " + filename + " CLOSE_OK";
                                        
                                        byte[] sendData = closeString.getBytes();
                                        InetAddress clientAddress = requestPacket.getAddress();
                                        int clientPort = requestPacket.getPort();
                                        DatagramPacket responsePacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                                        serverSocket.send(responsePacket);
                                        break;
                                    }
                                    
                                }

                                // Close the socket
                                clientSocket.close();
                            }

                            catch (Exception e) {
                                System.out.println("There was an error with the data port: " + e.getMessage());
                            }

                            }

                        };
                    
                    // Start the thread
                    clientThread.start();
                }
                else{
                    encodedString = "ERR " + filename + " NOT_FOUND";

                    // Send the response back to the client
                    byte[] sendData = encodedString.getBytes();
                    InetAddress clientAddress = requestPacket.getAddress();
                    int clientPort = requestPacket.getPort();
                    DatagramPacket responsePacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                    serverSocket.send(responsePacket);
                    break;
                }

            }

            // Close the socket
            serverSocket.close();
        } 
        catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }
}