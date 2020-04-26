import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Scanner;

public class Player {
    private static final int PORT = 1234, ROCK = 1, PAPER = 2, SCISSORS = 3, NUM_PLAYERS = 3;
    private static boolean started;
    private static final byte END = -1, START = 0;
    private static final String ADDR = "239.0.0.0";
    private static int numberPlayed=-1;
    private static int score;
    private static byte[] id;
    private static byte play;
    static volatile boolean done = false;

    public static void main(String[] args) {

        // Gets the process ID and stores it as a byte array to reference later.
        id = ByteBuffer.allocate(8).putLong(ProcessHandle.current().pid()).array();

        try{
            InetAddress group = InetAddress.getByName(ADDR);
            MulticastSocket socket = new MulticastSocket(PORT);
            socket.setTimeToLive(0);
            socket.joinGroup(group);

            /*
              Message receiving thread to listen for the plays of the other players. It reads in a 9 byte packet from
              the network group, with the first 8 bytes being the process ID and the 9th being the play from that
              process. If this is the second play, meaning all players have played this round, it adds to the score
              and signals the main thread to play another hand. This loops until it receives termination signals and
              finishes its last round.
             */
            new Thread(() -> {
                byte[] buffer = new byte[9];
                byte play1=0, play2;

                while(true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                    try {
                        socket.receive(packet);
                        if(!started && buffer[8]==START){
                            synchronized (ADDR) {
                                play1++;
                                System.out.println("Received start signal from " + play1 + " players");
                                if (play1 == NUM_PLAYERS) {
                                    started = true;
                                    System.out.println("Starting...");
                                    ADDR.notify();
                                }
                            }
                        } else if(!checkIfSender(buffer)) {
                            if(play1==0) {
                                if(buffer[8]==END){
                                    done = true;
                                    socket.leaveGroup(group);
                                    socket.close();
                                    break;
                                }
                                play1 = buffer[8];
                            }
                            else{
                                synchronized (ADDR) {
                                    play2 = buffer[8];
                                    if(play2!=END) {
                                        System.out.println("This player played " + intToPlay(play) +
                                                " 1 opponent picked " + intToPlay(play1) +
                                                " the other picked " + intToPlay(play2));
                                        score += score(play, play1, play2);
                                    }
                                    ADDR.notify();
                                    play1 = 0;
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            /*
             * Loop for user input on how many games to play, catches letter input and values that are 0 or less than.
             */
            int numGames = -1;
            Scanner in = new Scanner(System.in);
            while(numGames <= 0){
                try{
                    System.out.println("Enter the number of games for the players to play: ");
                    numGames = in.nextInt();
                } catch (Exception e){
                    System.out.println("Make sure to enter a number greater than 0");
                    numGames = -1;
                    in = new Scanner(System.in);
                }
            }
            int numberOfGames = numGames;

            /*
             * Below is the packet sending loop which sends out a play after each match. Once sending a play, it waits
             * for a signal from the match reading thread to send another game. If it has played all the games it sends
             * a termination signal and breaks from the loop.
             */
            while(!done){
                synchronized (ADDR) {
                    if (numberPlayed >= numberOfGames)
                        play = END;
                    else if(!started)
                        play = START;
                    else
                        play = pickPlay();
                    byte[] buffer = Arrays.copyOf(id, 9);
                    buffer[8] = play;

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                    socket.send(packet);
                    if (play == END)
                        break;
                    numberPlayed++;
                    if (!done)
                        ADDR.wait();
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("This player scored "+score+" in "+numberPlayed+" games");
    }

    /**
     * Helper method to see if a packet is sent by this player, compares the process ID to the process ID stored at the
     * packet.
     * @param buffer They byte[] for the data received from the packet.
     * @return True if buffer was sent by this process.
     */
    public static boolean checkIfSender(byte[] buffer){
        for(int i=0;i<8;i++)
            if(buffer[i]!=id[i])
                return false;
            return true;
    }

    /**
     * Helper method to pick a random play for Rock Paper Scissors
     * 1 - Rock, 2 - Paper, 2 - Scissors
     * @return A number representing the play
     */
    public static byte pickPlay(){
        return (byte)(Math.random()*3+1);
    }

    /**
     * Helper method to convert an int to a play.
     * @param play The int for the play
     * @return The string for the move
     */
    public static String intToPlay(int play){
        switch (play){
            case ROCK:
                return "Rock";
            case PAPER:
                return "Paper";
            case SCISSORS:
                return "Scissors";
            case END:
                return "TERMINATION SIGNAL";
            case START:
                return "STARTING SYMBOL";
            default:
                return "ERROR - "+play;
        }
    }

    /**
     * Calculates the player's score against two other opponents.
     * @param hand The player's hand
     * @param play1 The hand of the first opponent
     * @param play2 The hand of the second opponent
     * @return A score of 0, 1, or 2.
     */
    public static int score(int hand, int play1, int play2){
        int score = score(hand,play1)+score(hand,play2);
        return Math.max(score,0);
    }

    /**
     * Computes the score for one player against another.
     * @param hand The player's hand
     * @param play The hand of the opponent
     * @return A score of -1, 0, or 1.
     */
    public static int score(int hand, int play){
        if(hand == play)
            return 0;
        if(++hand%3+1 == play)
            return 1;
        return -1;
    }

}