import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Map;
import java.util.Objects;

public class Speleologist extends Agent {

    static int LOOK_RIGHT = 0;
    static int LOOK_LEFT = 1;
    static int LOOK_UP = 2;
    static int LOOK_DOWN = 3;
    static int MOVE = 4;
    static int SHOOT_ARROW = 5;
    static int TAKE_GOLD = 6;

    static String GO_INSIDE = "go_inside";
    static String WAMPUS_WORLD_TYPE = "wampus-world";
    static String NAVIGATOR_AGENT_TYPE = "navigator-agent";
    static String WORLD_DIGGER_CONVERSATION_ID = "digger-world";
    static String NAVIGATOR_DIGGER_CONVERSATION_ID = "digger-navigator";
    private AID wampusWorld;
    private AID navigationAgent;
    private String currentWorldState = "";

    static Map<Integer, String> actionCodes = Map.of(LOOK_RIGHT, "right", LOOK_LEFT, "left",
            LOOK_UP, "up", LOOK_DOWN, "down", MOVE, "move", SHOOT_ARROW, "shoot", TAKE_GOLD, "take");

    @Override
    protected void setup() {
        addBehaviour(new WampusWorldFinder());
    }

    private class WampusWorldFinder extends Behaviour {

        private int step = 0;

        @Override
        public void action() {
            if (step == 0) {
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType(WAMPUS_WORLD_TYPE);
                template.addServices(sd);
                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    if (result != null && result.length > 0) {
                        wampusWorld = result[0].getName();
                        myAgent.addBehaviour(new WampusWorldPerformer());
                        ++step;
                    } else {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (FIPAException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public boolean done() {
            return step == 1;
        }
    }

    private class WampusWorldPerformer extends Behaviour {
        private MessageTemplate mt;

        private int step = 0;

        @Override
        public void action() {
            switch (step) {
                case 0:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(wampusWorld);
                    cfp.setContent(GO_INSIDE);
                    cfp.setConversationId(WORLD_DIGGER_CONVERSATION_ID);
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(WORLD_DIGGER_CONVERSATION_ID),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.CONFIRM) {
                            currentWorldState = reply.getContent();
                            myAgent.addBehaviour(new NavigatorAgentPerformer());
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
            }
        }

        @Override
        public boolean done() {
            return step == 2;
        }
    }

    private class NavigatorAgentPerformer extends Behaviour {
        private int step = 0;
        private MessageTemplate mt;

        @Override
        public void action() {
            switch (step) {
                case 0: {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType(NAVIGATOR_AGENT_TYPE);
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result != null && result.length > 0) {
                            navigationAgent = result[0].getName();
                            ++step;
                        } else {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }
                }
                case 1: {
                    ACLMessage order = new ACLMessage(ACLMessage.INFORM);
                    order.addReceiver(navigationAgent);
                    order.setContent(currentWorldState);
                    order.setConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID);
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 2;
                }
                case 2: {
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            String actions = reply.getContent();
                            actions = actions.substring(1, actions.length() - 1);
                            String[] instructions = actions.split(", ");
                            if (instructions.length == 1) {
                                sendTakeGoldMessage();
                            } else if (instructions.length == 2 && Objects.equals(instructions[1], actionCodes.get(SHOOT_ARROW))) {
                                sendShootMessage(instructions[0]);
                            } else if (instructions.length == 2 && Objects.equals(instructions[1], actionCodes.get(MOVE))) {
                                sendMoveMessage(instructions[0]);
                            } else {
                                System.out.println("ERROR ACTIONS");
                            }
                            ++step;
                        }
                    } else {
                        block();
                    }
                    break;

                }
                case 3:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        currentWorldState = reply.getContent();
                        step = 1;
                    } else {
                        block();
                    }
                    break;
            }
        }

        @Override
        public boolean done() {
            return step == 4;
        }

        private void sendShootMessage(String instruction) {
            ACLMessage order = new ACLMessage(SHOOT_ARROW);
            order.addReceiver(wampusWorld);
            order.setContent(instruction);
            order.setConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID);
            order.setReplyWith("order" + System.currentTimeMillis());
            myAgent.send(order);
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
        }

        private void sendTakeGoldMessage() {
            ACLMessage order = new ACLMessage(TAKE_GOLD);
            order.addReceiver(wampusWorld);
            order.setContent("Take");
            order.setConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID);
            order.setReplyWith("order" + System.currentTimeMillis());
            myAgent.send(order);
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
        }

        private void sendMoveMessage(String instruction) {
            ACLMessage order = new ACLMessage(MOVE);
            order.addReceiver(wampusWorld);
            order.setContent(instruction);
            order.setConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID);
            order.setReplyWith("order" + System.currentTimeMillis());
            myAgent.send(order);
            mt = MessageTemplate.and(MessageTemplate.MatchConversationId(NAVIGATOR_DIGGER_CONVERSATION_ID),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
        }
    }
}