package com.github.rosjava.android_remocons.talker;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.github.rosjava.android_remocons.common_tools.apps.RosAppActivity;

import org.ros.android.MessageCallable;
import org.ros.android.view.RosTextView;
import org.ros.node.ConnectedNode;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.io.IOException;

public class Talker extends RosAppActivity
{
    private Toast lastToast;
    private ConnectedNode node;
    private RosTextView<std_msgs.String> rosTextView;

    public Talker()
    {
        super("Talker", "Talker");
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        setDefaultMasterName(getString(R.string.default_robot));
        setDashboardResource(R.id.top_bar);
        setMainWindowResource(R.layout.main);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        String chatterTopic = remaps.get(getString(R.string.chatter_topic));
        super.init(nodeMainExecutor);

        rosTextView = (RosTextView<std_msgs.String>) findViewById(R.id.text);
        rosTextView.setTopicName(getMasterNameSpace().resolve(chatterTopic).toString());
        rosTextView.setMessageType(std_msgs.String._TYPE);
        rosTextView.setMessageToStringCallable(new MessageCallable<String, std_msgs.String>() {
            @Override
            public java.lang.String call(std_msgs.String message) {
                Log.i("Talker", "received closed loop message [" + message.getData() + "]");
                return message.getData();
            }
        });

        try {
            // Really horrible hack till I work out exactly the root cause and fix for
            // https://github.com/rosjava/android_remocons/issues/47
            Thread.sleep(1000);
            java.net.Socket socket = new java.net.Socket(getMasterUri().getHost(), getMasterUri().getPort());
            java.net.InetAddress local_network_address = socket.getLocalAddress();
            socket.close();
            NodeConfiguration nodeConfiguration =
                    NodeConfiguration.newPublic(local_network_address.getHostAddress(), getMasterUri());
            Log.e("Talker", "master uri [" + getMasterUri() + "]");
            org.ros.rosjava_tutorial_pubsub.Talker talker = new org.ros.rosjava_tutorial_pubsub.Talker(getMasterNameSpace().resolve(chatterTopic).toString());
            nodeMainExecutor.execute(talker, nodeConfiguration);
            nodeMainExecutor.execute(rosTextView, nodeConfiguration);
        } catch(InterruptedException e) {
            // Thread interruption
            Log.e("Talker", "sleep interrupted");
        } catch (IOException e) {
            // Socket problem
            Log.e("Talker", "socket error trying to get networking information from the master uri");
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        menu.add(0,0,0,getString(R.string.stop_app));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()){
            case 0:
                finish();
                break;
        }
        return true;
    }
}



