package edu.buffalo.cse.cse486586.groupmessenger2;

public class Message implements Comparable{
    String message; // My message
    int portNum; // Who I am
    int proposalNum; // Proposal number
    int processNum; // Process number
    String status; // Message Status
    int seqNum; // SeqNum
    boolean isDeliverable = false; // Boolean variable to decide on persistance

    public Message(int seqNum, int proposalNum, int processNum, int portNum, String message, String status) {
        this.seqNum = seqNum;
        this.processNum = processNum;
        this.proposalNum = proposalNum;
        this.portNum = portNum;
        this.message = message;
        this.status = status;

    }

    @Override
    public String toString() {
        // Serialize the message with ";"
        // as delimiter

        StringBuilder res = new StringBuilder();

        res.append(seqNum);
        res.append(';');

        res.append(processNum);
        res.append(';');

        res.append(proposalNum);
        res.append(';');

        res.append(portNum);
        res.append(';');

        res.append(message);
        res.append(';');

        res.append(status);
        return res.toString();
    }

    @Override
    public int compareTo(Object another) {
        // Message comparator
        // Kinda useless tho
        Message m = (Message) another;
        if (this.proposalNum < m.proposalNum) {
            return -1;
        }
        else if (this.proposalNum == m.proposalNum) {
            if (this.processNum < m.processNum)
                return -1;
            else
                return 1;
        }
        else
            return 1;

    }
}
