import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class WumpusWorldAgent extends Agent {

    public static String SERVICE_DESCRIPTION = "WAMPUS-WORLD";
    private static int START = -1;
    private static int WAMPUS = 1;
    private static int PIT = 2;
    private static int BREEZE = 3;
    private static int STENCH = 4;
    private static int SCREAM = 5;
    private static int GOLD = 6;
    private static int BUMP = 7;
    public static Map<Integer, String> roomCodes = Map.of(START, Navigator.START, WAMPUS, Navigator.WAMPUS, PIT, Navigator.PIT,
            BREEZE, Navigator.BREEZE, STENCH, Navigator.STENCH, SCREAM, Navigator.SCREAM, GOLD, Navigator.GOLD, BUMP, Navigator.BUMP);

    private static int NUM_OF_ROWS = 4;
    private static int NUM_OF_COLUMNS = 4;

    private Room[][] wampusMap;
    private HashMap<AID, Coords> Speleologists;

    String nickname = "WampusWorld";
    AID id = new AID(nickname, AID.ISLOCALNAME);

    @Override
    protected void setup() {
        System.out.println("Hello! WampusWorld-agent " + getAID().getName() + " is ready.");
        Speleologists = new HashMap<>();
        generateMap();
        showMap();
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(Speleologist.WAMPUS_WORLD_TYPE);
        sd.setName(SERVICE_DESCRIPTION);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new SpeleologistConnectPerformer());
        addBehaviour(new SpeleologistArrowPerformer());
        addBehaviour(new SpeleologistGoldPerformer());
        addBehaviour(new SpeleologistMovePerformer());
    }

    private void generateMap() {
        this.wampusMap = new Room[NUM_OF_ROWS][NUM_OF_COLUMNS];
        this.wampusMap[0][0] = new Room();
        this.wampusMap[0][1] = new Room(BREEZE);
        this.wampusMap[0][2] = new Room(PIT);
        this.wampusMap[0][3] = new Room(BREEZE);
        this.wampusMap[1][0] = new Room(STENCH);
        this.wampusMap[1][3] = new Room(BREEZE);
        this.wampusMap[2][0] = new Room(WAMPUS, STENCH);
        this.wampusMap[2][1] = new Room(BREEZE, STENCH, GOLD);
        this.wampusMap[2][2] = new Room(PIT);
        this.wampusMap[2][3] = new Room(BREEZE);
        this.wampusMap[3][0] = new Room(STENCH);
        this.wampusMap[3][2] = new Room(BREEZE);
        this.wampusMap[3][3] = new Room(PIT);
        for (int i = 0; i < this.wampusMap.length; i++) {
            for (int j = 0; j < this.wampusMap[i].length; j++) {
                if (this.wampusMap[i][j] == null) {
                    this.wampusMap[i][j] = new Room();
                }
            }

        }

    }

    private void showMap() {
        for (int i = 0; i < this.wampusMap.length; i++) {
            for (int j = 0; j < this.wampusMap[i].length; j++) {
                System.out.println("POSITION: " + i + ", " + j + "; MARKERS: " + wampusMap[i][j].events);
            }

        }
    }

    private class SpeleologistConnectPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String message = msg.getContent();
                if (Objects.equals(message, Speleologist.GO_INSIDE)) {
                    AID current_Speleologist = msg.getSender();
                    Speleologists.put(current_Speleologist, new Coords(0, 0));
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    reply.setContent(wampusMap[0][0].events.toString());
                    myAgent.send(reply);
                }
//
            } else {
                block();
            }
        }
    }

    private class SpeleologistArrowPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(Speleologist.SHOOT_ARROW);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(Speleologist.SHOOT_ARROW);

                String message = msg.getContent();
                AID current_Speleologist = msg.getSender();
                Coords Speleologist_coords = Speleologists.get(current_Speleologist);

                int row = Speleologist_coords.row;
                int column = Speleologist_coords.column;
                String answer = "";
                if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_DOWN))) {
                    for (int i = 0; i < row; ++i) {
                        if (wampusMap[i][column].events.contains(WumpusWorldAgent.roomCodes.get(WAMPUS))) {
                            answer = Navigator.SCREAM;
                        }
                    }
                } else if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_UP))) {
                    for (int i = row + 1; i < NUM_OF_ROWS; ++i) {
                        if (wampusMap[i][column].events.contains(WumpusWorldAgent.roomCodes.get(WAMPUS))) {
                            answer = Navigator.SCREAM;
                        }
                    }
                } else if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_LEFT))) {
                    for (int i = 0; i < column; ++i) {
                        if (wampusMap[row][i].events.contains(WumpusWorldAgent.roomCodes.get(WAMPUS))) {
                            answer = Navigator.SCREAM;
                        }
                    }
                } else if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_RIGHT))) {
                    for (int i = column + 1; i < NUM_OF_COLUMNS; ++i) {
                        if (wampusMap[row][i].events.contains(WumpusWorldAgent.roomCodes.get(WAMPUS))) {
                            answer = Navigator.SCREAM;
                        }
                    }
                }

                reply.setContent(answer);

                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private class SpeleologistMovePerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(Speleologist.MOVE);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(Speleologist.MOVE);

                String message = msg.getContent();
                AID current_Speleologist = msg.getSender();
                Coords Speleologist_coords = Speleologists.get(current_Speleologist);
                System.out.println("World say: Current agent coords: " + Speleologist_coords.row + " | " + Speleologist_coords.column);
                if (Speleologist_coords == null) {
                    Speleologists.put(current_Speleologist, new Coords(0, 0));
                    Speleologist_coords = Speleologists.get(current_Speleologist);
                }
                int row = Speleologist_coords.row;
                int column = Speleologist_coords.column;
                if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_DOWN))) {
                    row -= 1;
                } else if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_UP))) {
                    row += 1;
                } else if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_LEFT))) {
                    column -= 1;
                } else if (message.equals(Speleologist.actionCodes.get(Speleologist.LOOK_RIGHT))) {
                    column += 1;
                }
                if (row > -1 && column > -1 && row < NUM_OF_ROWS && column < NUM_OF_COLUMNS) {
                    Speleologist_coords.column = column;
                    Speleologist_coords.row = row;
                    reply.setContent(wampusMap[row][column].events.toString());
                } else {
                    reply.setContent(String.valueOf(new ArrayList<String>() {{
                        add(Navigator.BUMP);
                    }}));
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private class SpeleologistGoldPerformer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(Speleologist.TAKE_GOLD);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                AID current_Speleologist = msg.getSender();
                Coords Speleologist_coords = Speleologists.get(current_Speleologist);
                if (Speleologist_coords == null) {
                    Speleologists.put(current_Speleologist, new Coords(0, 0));
                } else {
                    if (wampusMap[Speleologist_coords.row][Speleologist_coords.column].events.contains(WumpusWorldAgent.roomCodes.get(GOLD))) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(Speleologist.TAKE_GOLD);
                        reply.setContent("GOLD");
                        myAgent.send(reply);
                    }
                }
            } else {
                block();
            }
        }
    }
}

class Room {
    List<String> events = new ArrayList<>();

    Room(int... args) {
        for (int arg : args) {
            events.add(WumpusWorldAgent.roomCodes.get(arg));
        }
    }
}

class Coords {
    int row;
    int column;

    Coords(int row, int column) {
        this.row = row;
        this.column = column;
    }
}