// package Controllers;

// import Model.Room;

// public class RoomController {


//     public static Room createRoom(BufferedReader reader, PrintWriter writer, String clientId) throws IOException {
//         writer.println("Enter new room name:");
//         String roomName = reader.readLine();
//         if (roomName == null || roomName.trim().isEmpty()) {
//             writer.println("Invalid room name");
//             return;
//         }

//         writer.println("How many members can the room have?");
//         int noMembers;
//         try {
//             noMembers = Integer.parseInt(reader.readLine());
//             if (noMembers <= 0) {
//                 writer.println("Error: Member count must be positive");
//                 return;
//             }
//         } catch (NumberFormatException | IOException e) {
//             writer.println("Error: Please enter a valid number");
//             return;
//         }

//         writer.println("AI Integration in bot? [y/n]");
//         boolean aiRoom;
//         String aiResponse = reader.readLine().toLowerCase();
//         if (aiResponse.equals("y")) {
//             aiRoom = true;
//         } else if (aiResponse.equals("n")) {
//             aiRoom = false;
//         } else {
//             writer.println("Error: Please answer with 'y' or 'n'");
//             return;
//         }

//         roomLock.lock();
//         try {
//             boolean exists = rooms.stream()
//                 .anyMatch(r -> r.getName().equals(roomName));

//             if (exists) {
//                 writer.println("Room name already exists");
//             } else {
//                 Room newRoom = new Room(roomName, noMembers, aiRoom);
//                 Client client = findClient(clientId);
//                 if (client != null) {
//                     newRoom.addClient(client);
//                     rooms.add(newRoom);
//                     clients.removeIf(c -> c.getName().equals(clientId));
//                     writer.println("Created and joined room: " + roomName);
//                 } else {
//                     writer.println("Error: Client not found");
//                 }
//             }
//         } finally {
//             roomLock.unlock();
//         }
//     }
// }
