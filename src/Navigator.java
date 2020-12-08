import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Navigator extends Agent {
    static final String START = "start";
    static final String WAMPUS = "wampus";
    static final String PIT = "pit";
    static final String BREEZE = "breeze";
    static final String STENCH = "stench";
    static final String SCREAM = "scream";
    static final String GOLD = "gold";
    static final String BUMP = "bump";
    static int ROOM_STATUS_TRUE = 1;
    static int ROOM_STATUS_FALSE = 2;
    static int ROOM_STATUS_POSSIBLE = 3;
    static int ROOM_STATUS_NO_STATUS = -1;


    private static final String SERVICE_DESCRIPTION = "NAVIGATOR_AGENT";
    private String nickname = "Navigator";
    private AID id = new AID(nickname, AID.ISLOCALNAME);
    private Hashtable<AID, Position> agents_coords;
    private Hashtable<AID, LinkedList<int[]>> agentsWayStory;

    private boolean moveRoom = false;
    private int agentX;
    private int agentY;

    private ImaginaryWampusWorld world;

    @Override
    protected void setup() {
        world = new ImaginaryWampusWorld();
        agentsWayStory = new Hashtable<>();
        agents_coords = new Hashtable<>();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(Speleologist.NAVIGATOR_AGENT_TYPE);
        sd.setName(SERVICE_DESCRIPTION);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new LocationRequestsServer());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Navigator-agent " + getAID().getName() + " terminating.");
    }

    private class LocationRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                AID request_agent = msg.getSender();
                if (agentsWayStory.get(request_agent) == null) {
                    LinkedList<int[]> agentWay = new LinkedList<>();
                    agentsWayStory.put(request_agent, agentWay);
                }
                Position request_agent_position = agents_coords.get(request_agent);
                if (request_agent_position == null) {
                    request_agent_position = new Position();
                    agents_coords.put(request_agent, request_agent_position);
                }
                String location = msg.getContent();
                location = location.substring(1, location.length() - 1);
                String[] room_info = location.split(", ");
                System.out.println("ROOM INFO: " + Arrays.toString(room_info));
                System.out.println("AGENT INFO: " + request_agent_position.getX() + " " + request_agent_position.getY());
                String[] actions = get_actions(request_agent, request_agent_position, room_info);
                ACLMessage reply = msg.createReply();

                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(Arrays.toString(actions));
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private String[] get_actions(AID request_agent, Position request_agent_position, String[] room_info) {
        System.out.println("Agent pos before: " + request_agent_position.getX() + " | " + request_agent_position.getY());
        int[] actions;
        ImaginaryRoom checking_room = world.getWorldGrid().get(request_agent_position);
        if (checking_room == null) {
            checking_room = new ImaginaryRoom();
            world.getWorldGrid().put(request_agent_position, checking_room);
        }

        if (!Arrays.asList(room_info).contains(BUMP)) {
            LinkedList<int[]> agentStory = agentsWayStory.get(request_agent);
            agentStory.add(new int[]{request_agent_position.getX(), request_agent_position.getY()});
            request_agent_position.setX(agentX);
            request_agent_position.setY(agentY);
            if (world.getWorldGrid().get(request_agent_position).getExist() != Navigator.ROOM_STATUS_TRUE) {
                world.getWorldGrid().get(request_agent_position).setExist(Navigator.ROOM_STATUS_TRUE);
                System.out.println("MARKED THE EXISTENCE");
            }
            moveRoom = false;
        } else {
            Position helpPosition = new Position(agentX, agentY);
            world.getWorldGrid().get(helpPosition).setExist(Navigator.ROOM_STATUS_FALSE);
        }
        checking_room = world.getWorldGrid().get(request_agent_position);
        if (checking_room == null) {
            checking_room = new ImaginaryRoom();
            world.getWorldGrid().put(request_agent_position, checking_room);
        }

        if (checking_room.getOk() != Navigator.ROOM_STATUS_TRUE) {
            checking_room.setOk(Navigator.ROOM_STATUS_TRUE);
        }
        for (String event : room_info) {
            checking_room.addEvent(event);
        }
        updateNeighbors(request_agent_position);
        if (world.isWampusAlive() && world.getWampusRoomCount() > 2) {
            Position wampusPosition = world.getWampusCoords();
            actions = getNextRoomAction(request_agent_position, wampusPosition, Speleologist.SHOOT_ARROW);
        } else {
            Position[] nextOkRooms = getOkNeighbors(request_agent, request_agent_position);
            int best_candidate = -1;
            int candidate_status = -1;
            for (int i = 0; i < nextOkRooms.length; ++i) {
                Position candidate_room = nextOkRooms[i];
                System.out.println("CANDIDATE CHECKING: " + candidate_room.getX() + " " + candidate_room.getY());
                System.out.println("AGENT CHECKING: " + request_agent_position.getX() + " " + request_agent_position.getY());
                if (candidate_room.getX() > request_agent_position.getX()) {
                    best_candidate = i;
                    System.out.println("1");
                    break;
                } else if (candidate_room.getY() > request_agent_position.getY()) {
                    if (candidate_status < 3) {
                        System.out.println("2");
                        candidate_status = 3;
                    } else continue;
                } else if (candidate_room.getX() < request_agent_position.getX()) { // влево
                    if (candidate_status < 2) {
                        System.out.println("3");
                        candidate_status = 2;
                    } else continue;
                } else { // вниз
                    if (candidate_status < 1) {
                        System.out.println("4");
                        candidate_status = 1;
                    } else continue;
                }
                best_candidate = i;
            }
            System.out.println("OK ROOMS COUNT IS: " + nextOkRooms.length);
            System.out.println("ADVICE POSITION IS: " + nextOkRooms[best_candidate].getX() + " | " + nextOkRooms[best_candidate].getY());
            actions = getNextRoomAction(request_agent_position, nextOkRooms[best_candidate], Speleologist.MOVE);
            System.out.println("ADVICE ACTIONS IS: " + Arrays.toString(actions));
        }

        String[] language_actions = new String[actions.length];
        for (int i = 0; i < actions.length; ++i) {
            language_actions[i] = Speleologist.actionCodes.get(actions[i]);
        }
        return language_actions;
    }

    private int[] getNextRoomAction(Position request_agent_position, Position nextOkRoom, int action) {
        agentX = request_agent_position.getX();
        agentY = request_agent_position.getY();
        int look;
        if (request_agent_position.getY() < nextOkRoom.getY()) {
            agentY += 1;
            look = Speleologist.LOOK_UP;
        } else if (request_agent_position.getY() > nextOkRoom.getY()) {
            agentY -= 1;
            look = Speleologist.LOOK_DOWN;
        } else if (request_agent_position.getX() < nextOkRoom.getX()) {
            agentX += 1;
            look = Speleologist.LOOK_RIGHT;
        } else {
            agentX -= 1;
            look = Speleologist.LOOK_LEFT;
        }
        moveRoom = true;

        return new int[]{look, action};
    }

    private Position[] getOkNeighbors(AID request_agent, Position request_agent_position) {
        Position[] okNeighbors = getNeighborsPosition(request_agent_position);
        List<Position> okPositions = new ArrayList<>();
        for (Position position : okNeighbors) {
            this.world.getWorldGrid().putIfAbsent(position, new ImaginaryRoom()); // если комнаты
            // не существует - добавляем новую комнату на карте
            if ((this.world.getWorldGrid().get(position).getOk() == Navigator.ROOM_STATUS_TRUE
                        && this.world.getWorldGrid().get(position).getNoWay() != Navigator.ROOM_STATUS_TRUE
                        && this.world.getWorldGrid().get(position).getExist() != Navigator.ROOM_STATUS_FALSE)
                    || this.world.getWorldGrid().get(position).getOk() == Navigator.ROOM_STATUS_NO_STATUS) {
                okPositions.add(position);
            }
        }
        if (okPositions.size() == 0) {
            int x = agentsWayStory.get(request_agent).getLast()[0];
            int y = agentsWayStory.get(request_agent).getLast()[1];
            okPositions.add(new Position(x, y));
            this.world.getWorldGrid().get(request_agent_position).setNoWay(ROOM_STATUS_TRUE);
        }
        return okPositions.toArray(new Position[0]);
    }

    private ImaginaryRoom[] getNeighborsImaginaryRoom(Position request_agent_position) {
        Position rightNeighbor = new Position(request_agent_position.getX() + 1, request_agent_position.getY());
        Position upNeighbor = new Position(request_agent_position.getX(), request_agent_position.getY() + 1);
        Position leftNeighbor = new Position(request_agent_position.getX() - 1, request_agent_position.getY());
        Position bottomNeighbor = new Position(request_agent_position.getX(), request_agent_position.getY() - 1);
        ImaginaryRoom rightRoom = world.getWorldGrid().get(rightNeighbor);
        if (rightRoom == null) {
            rightRoom = new ImaginaryRoom();
            world.getWorldGrid().put(rightNeighbor, rightRoom);
        }
        ImaginaryRoom upRoom = world.getWorldGrid().get(upNeighbor);
        if (upRoom == null) {
            upRoom = new ImaginaryRoom();
            world.getWorldGrid().put(rightNeighbor, upRoom);
        }
        ImaginaryRoom leftRoom = world.getWorldGrid().get(leftNeighbor);
        if (leftRoom == null) {
            leftRoom = new ImaginaryRoom();
            world.getWorldGrid().put(rightNeighbor, leftRoom);
        }
        ImaginaryRoom bottomRoom = world.getWorldGrid().get(bottomNeighbor);
        if (bottomRoom == null) {
            bottomRoom = new ImaginaryRoom();
            world.getWorldGrid().put(rightNeighbor, bottomRoom);
        }
        return new ImaginaryRoom[]{rightRoom, upRoom, leftRoom, bottomRoom};
    }

    private Position[] getNeighborsPosition(Position request_agent_position) {
        Position rightNeighbor = new Position(request_agent_position.getX() + 1, request_agent_position.getY());
        Position upNeighbor = new Position(request_agent_position.getX(), request_agent_position.getY() + 1);
        Position leftNeighbor = new Position(request_agent_position.getX() - 1, request_agent_position.getY());
        Position bottomNeighbor = new Position(request_agent_position.getX(), request_agent_position.getY() - 1);

        return new Position[]{rightNeighbor, upNeighbor, leftNeighbor, bottomNeighbor};
    }

    private void updateNeighbors(Position request_agent_position) {
        ImaginaryRoom currentRoom = world.getWorldGrid().get(request_agent_position);
        ImaginaryRoom[] roomList = getNeighborsImaginaryRoom(request_agent_position);

        if (currentRoom.getStench() == Navigator.ROOM_STATUS_TRUE) {
            world.setWampusRoomCount(world.getWampusRoomCount() + 1);
            for (ImaginaryRoom room : roomList) {
                if (room.getWampus() == Navigator.ROOM_STATUS_NO_STATUS) {
                    room.setOk(Navigator.ROOM_STATUS_POSSIBLE);
                    room.setWampus(Navigator.ROOM_STATUS_POSSIBLE);
                }
            }
        }
        if (currentRoom.getBreeze() == Navigator.ROOM_STATUS_TRUE) {
            for (ImaginaryRoom room : roomList) {
                if (room.getPit() == Navigator.ROOM_STATUS_NO_STATUS) {
                    room.setOk(Navigator.ROOM_STATUS_POSSIBLE);
                    room.setPit(Navigator.ROOM_STATUS_POSSIBLE);
                }
            }
        }
        if (currentRoom.getBreeze() == Navigator.ROOM_STATUS_FALSE && currentRoom.getStench() == Navigator.ROOM_STATUS_FALSE) {
            for (ImaginaryRoom room : roomList) {
                room.setOk(Navigator.ROOM_STATUS_TRUE);
                room.setWampus(Navigator.ROOM_STATUS_FALSE);
                room.setPit(Navigator.ROOM_STATUS_FALSE);
            }
        }
    }

}

class ImaginaryWampusWorld {

    private Hashtable<Position, ImaginaryRoom> worldGrid;
    private boolean isWampusAlive;
    private int wampusRoomCount;
    private Position wampusCoords;

    ImaginaryWampusWorld() {
        worldGrid = new Hashtable<>();
        isWampusAlive = true;
        wampusRoomCount = 0;
    }

    public Position getWampusCoords() {
        int xWampusCoord = 0;
        int yWampusCoord = 0;

        Set<Position> keys = worldGrid.keySet();
        for (Position roomPosition : keys) {
            ImaginaryRoom room = worldGrid.get(roomPosition);
            if (room.getWampus() == Navigator.ROOM_STATUS_POSSIBLE) {
                xWampusCoord += roomPosition.getX();
                yWampusCoord += roomPosition.getY();
            }
        }
        xWampusCoord /= wampusRoomCount;
        yWampusCoord /= wampusRoomCount;
        this.wampusCoords = new Position(xWampusCoord, yWampusCoord);
        return this.wampusCoords;
    }

    public Hashtable<Position, ImaginaryRoom> getWorldGrid() {
        return worldGrid;
    }


    public boolean isWampusAlive() {
        return isWampusAlive;
    }

    public void setWampusAlive(boolean wampusAlive) {
        isWampusAlive = wampusAlive;
    }

    public int getWampusRoomCount() {
        return wampusRoomCount;
    }

    public void setWampusRoomCount(int wampusRoomCount) {
        this.wampusRoomCount = wampusRoomCount;
    }
}

class ImaginaryRoom {
    private int exist;
    private int stench;
    private int breeze;
    private int pit;
    private int wampus;
    private int ok;
    private int gold;
    private int noWay;

    public ImaginaryRoom() {
        this.exist = Navigator.ROOM_STATUS_NO_STATUS;
        this.stench = Navigator.ROOM_STATUS_NO_STATUS;
        this.breeze = Navigator.ROOM_STATUS_NO_STATUS;
        this.pit = Navigator.ROOM_STATUS_NO_STATUS;
        this.wampus = Navigator.ROOM_STATUS_NO_STATUS;
        this.ok = Navigator.ROOM_STATUS_NO_STATUS;
        this.gold = Navigator.ROOM_STATUS_NO_STATUS;
        this.noWay = Navigator.ROOM_STATUS_NO_STATUS;
    }

    public void addEvent(String event_name) {
        switch (event_name) {
            case Navigator.START:
                break;
            case Navigator.WAMPUS:
                this.setWampus(Navigator.ROOM_STATUS_TRUE);
                break;
            case Navigator.PIT:
                this.setPit(Navigator.ROOM_STATUS_TRUE);
                break;
            case Navigator.BREEZE:
                this.setBreeze(Navigator.ROOM_STATUS_TRUE);
                break;
            case Navigator.STENCH:
                this.setStench(Navigator.ROOM_STATUS_TRUE);
                break;
            case Navigator.SCREAM:
                break;
            case Navigator.GOLD:
                this.setGold(Navigator.ROOM_STATUS_TRUE);
                break;
            case Navigator.BUMP:
                break;
        }
    }

    public int getExist() {
        return exist;
    }

    public void setExist(int exist) {
        this.exist = exist;
    }

    public int getStench() {
        return stench;
    }

    public void setStench(int stench) {
        this.stench = stench;
    }

    public int getBreeze() {
        return breeze;
    }

    public void setBreeze(int breeze) {
        this.breeze = breeze;
    }

    public int getPit() {
        return pit;
    }

    public void setPit(int pit) {
        this.pit = pit;
    }

    public int getWampus() {
        return wampus;
    }

    public void setWampus(int wampus) {
        this.wampus = wampus;
    }

    public int getOk() {
        return ok;
    }

    public void setOk(int ok) {
        this.ok = ok;
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public int getNoWay() {
        return noWay;
    }

    public void setNoWay(int noWay) {
        this.noWay = noWay;
    }

}

class Position {
    private int x;
    private int y;

    Position() {
        this.x = 0;
        this.y = 0;
    }

    Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        Position position = (Position) obj;
        return this.x == position.getX() && this.y == position.getY();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}