package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;


/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {

    TextView tv = null;

    // One lock to rule them all
    ReentrantLock lock = new ReentrantLock();

    // Message number for this AVD
    int messageSequenceNum = 0;

    // Hardcoded ports
    final int[] portsToSend = {11108, 11112, 11116, 11120, 11124};

    // This AVD's proposal number
    int propNum = 0;

    // Mapping client port number to socket
    HashMap<Integer, Socket> clientSocketMap = new HashMap<Integer, Socket>();

    // (Reverse) Mapping socket to client port number
    HashMap<Socket, Integer> serverPortMap = new HashMap<Socket, Integer>();

    // Who failed in phase-2
    Integer isFailed = null;

    // Content value key
    int contentValueKey = 0;

    // Generate message key for a message
    public String messageKey(Message m) {
        return m.portNum + "" + m.message + m.seqNum;
    }

    // Function to check same message
    public boolean isSameMessage(Message m1, Message m2) {
        return messageKey(m1).equals(messageKey(m2));
    }

    // Hashmap to store maximum message for agreement during consensus
    HashMap<String, Message> maxMessageHashMap = new HashMap<String, Message>();

    // The priority queue to maintain total-ordering
    PriorityQueue<Message> totalQueue = new PriorityQueue<Message>(1000, new Comparator<Message>() {
        @Override
        public int compare(Message lhs, Message rhs) {
            // Message ordering for x.y format
            if (lhs.proposalNum < rhs.proposalNum) {
                return -1;
            } else if (lhs.proposalNum == rhs.proposalNum) {
                if (lhs.processNum < rhs.processNum)
                    return -1;
                else
                    return 1;
            } else
                return 1;
        }
    });


    // URI builder
    public final Uri.Builder builder = new Uri.Builder();

    // Get this port number
    TelephonyManager tel = null;
    String portStr;
    Integer myPort;

    public void updateMaxMessageHashMap(Message m) {
        // Update max entry if necessary
        lock.lock();
        String key = messageKey(m);
        if (maxMessageHashMap.containsKey(key)) {

            Message toPut = maxMessageHashMap.get(key);

            if (m.proposalNum > toPut.proposalNum || (m.proposalNum == toPut.proposalNum && m.processNum > toPut.processNum))
                toPut = unserialize(m.toString());

            maxMessageHashMap.put(key, toPut);
        } else
            maxMessageHashMap.put(key, m);

        lock.unlock();
    }


    public void updatePriorityQueue(Message m) {

        // Update priority entry if necessary
        // Referred - https://stackoverflow.com/questions/1871253/updating-java-priorityqueue-when-its-elements-change-priority

        Log.v("Update pq", m.toString());

        AtomicBoolean flag = new AtomicBoolean(true);

        for (Message messageInQueue : totalQueue) {
            if (isSameMessage(messageInQueue, m)) {
                if (m.proposalNum > messageInQueue.proposalNum
                        || (m.proposalNum == messageInQueue.proposalNum &&
                        m.processNum > messageInQueue.processNum)) {

                    totalQueue.remove(messageInQueue);
                    totalQueue.offer(m);

                    flag.set(false);
                    break;
                } else if (m.processNum == messageInQueue.processNum && m.proposalNum == messageInQueue.proposalNum) {
                    totalQueue.remove(messageInQueue);
                    totalQueue.offer(m);
                    flag.set(false);
                    break;
                }
            }
        }
        if (flag.get())
            totalQueue.offer(m);
    }

    public void popQueue() {
        // Pop from queue to persistent storage

        // Pop if head of node is a message from a failed node
        if (isFailed != null)
            while (totalQueue.peek() != null && totalQueue.peek().portNum == isFailed && !totalQueue.peek().isDeliverable)
                totalQueue.poll();

        // Write to persistent storage
        while (totalQueue.peek() != null && totalQueue.peek().isDeliverable) {
            ContentValues contentValues = new ContentValues();
            Message top = totalQueue.poll();
            contentValues.put("key", Integer.toString(contentValueKey++));
            contentValues.put("value", top.message);
            getContentResolver().insert(builder.build(), contentValues);
        }

        Log.v("PQ Size", totalQueue.size() + "");

    }

    public Message unserialize(String string) {

        // Convert string to Message object
        String[] splitted = string.split("\\;");
        int seqNum = Integer.parseInt(splitted[0]);
        int processNum = Integer.parseInt(splitted[1]);
        int proposalNum = Integer.parseInt(splitted[2]);
        int portNum = Integer.parseInt(splitted[3]);
        String message = splitted[4];
        String status = splitted[5];

        return new Message(seqNum, proposalNum, processNum, portNum, message, status);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        builder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        builder.scheme("content");

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = Integer.parseInt(portStr) * 2;


        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        // Server listens at 10000
        try {
            ServerSocket serverSocket = new ServerSocket(10000);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            e.printStackTrace();
        }

        Button send = (Button) findViewById(R.id.button4);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText et = (EditText) findViewById(R.id.editText1);
                final String message = et.getText().toString();

                et.setText("");
                tv.append("\n" + message);

                Message m = new Message(++messageSequenceNum, -1, myPort, myPort, message, "PROPOSAL");

                // Send message
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, m.toString());

            }
        });
    }


    private class ServerTask extends AsyncTask<ServerSocket, Message, Void> {

        private class ServerThread implements Runnable {

            // Referred - https://stackoverflow.com/questions/10131377/socket-programming-multiple-client-to-one-server
            Socket clientSocket;

            ServerThread(Socket socket) {
                clientSocket = socket;

                DataInputStream dis = null;
                InputStream is = null;

                DataOutputStream dos = null;
                OutputStream os = null;
            }

            @Override
            public void run() {
                DataInputStream dis = null;
                InputStream is = null;
                Message m = null;
                DataOutputStream dos = null;
                OutputStream os = null;
                try {
                    dis = new DataInputStream(clientSocket.getInputStream());
                    dos = new DataOutputStream(clientSocket.getOutputStream());
                } catch (Exception e) {
                    if (isFailed == null) {
                        isFailed = serverPortMap.get(clientSocket);
                        Log.v("Failed", isFailed + "");
                    }
                }

                while (true) {
                    try {
                        m = unserialize(dis.readUTF());
                        // Adding entry for client hashmap
                        if (!serverPortMap.containsKey(clientSocket)) {
                            serverPortMap.put(clientSocket, m.processNum);
                            Log.v("Adding", m.processNum + "");
                        }

                    } catch (Exception e) {
                        if (isFailed == null) {
                            isFailed = serverPortMap.get(clientSocket);
                            Log.v("Failed", isFailed + "");
                        }
                    }

                    if (m.status.equals("PROPOSAL")) {
                        Log.d("sReceived", m.toString());

                        lock.lock();
                        m.proposalNum = ++propNum;
                        m.status = "PROPOSAL_REPLY";
                        m.processNum = myPort;
                        lock.unlock();

                        Log.d("Sending", m.toString());

                        try {
                            lock.lock();
                            updatePriorityQueue(m);
                            lock.unlock();

                            dos.writeUTF(m.toString());
                            dos.flush();
                        } catch (Exception e) {
                            if (isFailed == null) {
                                isFailed = serverPortMap.get(clientSocket);
                                Log.v("Failed", isFailed + "");
                            }
                        }

                        try {
                            String agreement = dis.readUTF();
                            lock.lock();
                            Message agreementMessage = unserialize(agreement);
                            agreementMessage.isDeliverable = true;
                            updatePriorityQueue(agreementMessage);
                            propNum = Math.max(propNum, agreementMessage.proposalNum);
                            popQueue();
                            lock.unlock();
                        } catch (Exception e) {
                            if (isFailed == null) {
                                isFailed = serverPortMap.get(clientSocket);
                                Log.v("Failed", isFailed + "");
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected Void doInBackground(ServerSocket... serverSockets) {

            ServerSocket serverSocket = serverSockets[0];
            ObjectInputStream ois = null;
            InputStream is = null;
            try {
                while (true) {
                    Socket socket = serverSocket.accept();
                    new Thread(new ServerThread(socket)).start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {

            String message = strings[0];
            Socket socket;
            Message m = null;
            String key = null;
            // Devil's 3-way
            for (int itr = 0; itr < portsToSend.length; itr++) {
                try {
                    if (clientSocketMap.get(portsToSend[itr]) == null) {
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), portsToSend[itr]);
                        // Reasonable timeout is reasonable
                        socket.setSoTimeout(3000);
                        clientSocketMap.put(portsToSend[itr], socket);
                    } else
                        socket = clientSocketMap.get(portsToSend[itr]);

                    if (isFailed != null && isFailed == portsToSend[itr])
                        continue;

                    OutputStream outputStream = socket.getOutputStream();
                    InputStream inputStream = socket.getInputStream();

                    DataOutputStream dos = new DataOutputStream(outputStream);
                    dos.writeUTF(message);
                    dos.flush();

                    DataInputStream dataInputStream = new DataInputStream(inputStream);

                    m = unserialize(dataInputStream.readUTF());
                    Log.v("cReceived", m.toString());

                    updateMaxMessageHashMap(m);
                    key = messageKey(m);

                } catch (Exception e) {
                    if (isFailed == null) {
                        isFailed = portsToSend[itr];
                        Log.v("Failed", portsToSend[itr] + "");
                    }
                }
            }


            Message toSend = maxMessageHashMap.get(key);

            // Consensus achieved
            toSend.status = "AGREEMENT";
            toSend.isDeliverable = true;

            Log.v("Agreed on", toSend.toString());
            lock.lock();
            propNum = Math.max(propNum, toSend.proposalNum);
            lock.unlock();
            int j = 0;

            // Send agreed messages to all
            try {
                for (; j < portsToSend.length; j++) {

                    if (isFailed != null && isFailed == portsToSend[j])
                        continue;

                    Socket socket_ = clientSocketMap.get(portsToSend[j]);
                    DataOutputStream dos_ = new DataOutputStream(socket_.getOutputStream());
                    dos_.writeUTF(toSend.toString());
                    dos_.flush();
                }
            } catch (Exception e) {
                if (isFailed == null) {
                    isFailed = portsToSend[j];
                    Log.v("Failed", portsToSend[j] + "");
                }
            }
            return null;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
