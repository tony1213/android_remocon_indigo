package com.github.rosjava.android_remocons.rocon_remocon;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.github.rosjava.android_remocons.common_tools.master.ConcertChecker;
import com.github.rosjava.android_remocons.common_tools.master.MasterId;
import com.github.rosjava.android_remocons.common_tools.master.RoconDescription;
import com.github.rosjava.android_remocons.common_tools.rocon.AppLauncher;
import com.github.rosjava.android_remocons.common_tools.rocon.InteractionsManager;
import com.github.rosjava.android_remocons.common_tools.system.WifiChecker;
import com.google.common.base.Preconditions;

import org.ros.android.RosActivity;
import org.ros.exception.RemoteException;
import org.ros.exception.RosRuntimeException;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;
import org.ros.node.service.ServiceResponseListener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import rocon_interaction_msgs.GetInteractionsResponse;
import rocon_interaction_msgs.Interaction;

public class MainActivity extends RosActivity {

    private final static String TAG = "MainActivity";

    private ArrayList<Interaction> availableAppsCache;
    private Interaction selectedInteraction;
    private RoconDescription roconDescription;
    private InteractionsManager interactionsManager;
    private StatusPublisher statusPublisher;
    private PairSubscriber pairSubscriber;
    private boolean validatedConcert;


    public MainActivity(){
        super("MainActivity","MainActivity");
        availableAppsCache = new ArrayList<Interaction>();
        statusPublisher = StatusPublisher.getInstance();
        pairSubscriber= PairSubscriber.getInstance();
        pairSubscriber.setAppHash(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button=(Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, MasterChooserService.class));
            }
        });

        IntentFilter filter=new IntentFilter();
        filter.addAction("com.robot.et.rocon");
        registerReceiver(receiver, filter);
        // Prepare the app manager; we do here instead of on init to keep using the same instance when switching roles
        interactionsManager = new InteractionsManager(
                new InteractionsManager.FailureHandler() {
                    public void handleFailure(String reason) {
                        Log.e(TAG, "Failure on interactions manager: " + reason);
                    }
                }
        );
        interactionsManager.setupGetInteractionsService(new ServiceResponseListener<GetInteractionsResponse>() {
            @Override
            public void onSuccess(rocon_interaction_msgs.GetInteractionsResponse response) {
                List<Interaction> apps = response.getInteractions();
                if (apps.size() > 0) {
                    availableAppsCache = (ArrayList<Interaction>) apps;
                    Log.i(TAG, "Interaction Publication: " + availableAppsCache.size() + " apps");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateAppList(availableAppsCache, roconDescription.getMasterName(), roconDescription.getCurrentRole());
                        }
                    });
                } else {
                    // TODO: maybe I should notify the user... he will think something is wrong!
                    Log.e(TAG, "No interactions available for the '" + roconDescription.getCurrentRole() + "' role.");
                }
            }

            @Override
            public void onFailure(RemoteException e) {
                Log.e(TAG, "retrieve interactions for the role '" + roconDescription.getCurrentRole() + "' failed: " + e.getMessage());
            }
        });
        interactionsManager.setupRequestService(new ServiceResponseListener<rocon_interaction_msgs.RequestInteractionResponse>() {
            @Override
            public void onSuccess(rocon_interaction_msgs.RequestInteractionResponse response) {
                Preconditions.checkNotNull(selectedInteraction);
                final boolean allowed = response.getResult();
                final String reason = response.getMessage();
                boolean ret_launcher_dialog = false;
                if(AppLauncher.checkAppType(selectedInteraction.getName()) == AppLauncher.AppType.NOTHING){
                    pairSubscriber.setAppHash(selectedInteraction.getHash());
                    ret_launcher_dialog = true;
                } else{
                    if(allowed){
                        pairSubscriber.setAppHash(selectedInteraction.getHash());
                    } else{
                        pairSubscriber.setAppHash(0);
                    }
                }
                if (ret_launcher_dialog) {
                    Log.i(TAG, "Selected Launch button");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            AppLauncher.Result result = AppLauncher.launch(MainActivity.this, roconDescription, selectedInteraction);
                            if (result == AppLauncher.Result.SUCCESS) {
                                Log.e(TAG,"Android app launch success");
                                // App successfully launched! Notify the concert and finish this activity
                                //statusPublisher.update(true, selectedInteraction.getHash(), selectedInteraction.getName());
                                // TODO try to no finish so statusPublisher remains while on app;  risky, but seems to work!    finish();
                            } else if (result == AppLauncher.Result.NOTHING){
                                //statusPublisher.update(false, selectedInteraction.getHash(), selectedInteraction.getName());
                            } else if (result == AppLauncher.Result.NOT_INSTALLED) {
                                // App not installed; ask for going to play store to download the missing app
                                Log.e(TAG,"Android app not installed.");
                                statusPublisher.update(false, 0, null);
                                selectedInteraction = null;
                            } else {
                                Log.e(TAG,"Cannot start app");
                            }
                        };
                    });
                } else {
                    Log.i(TAG,"User select cancel");
                    statusPublisher.update(false, 0, null);
                }
            }
            @Override
            public void onFailure(RemoteException e) {
                Log.e(TAG, "Retrieve rapps for role " + roconDescription.getCurrentRole() + " failed: " + e.getMessage());
            }
        });
        pairSubscriber.setAppHash(0);
    }

    BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.robot.et.rocon")){
                Log.e(TAG,"接收到数据");
                roconDescription=(RoconDescription)intent.getSerializableExtra("RoconDescription");
                init2(roconDescription);
            }
        }
    };

    void init2(RoconDescription roconDescription) {
        Log.e(TAG,"init2");
        URI uri;
        try {
            validatedConcert = false;
            validateConcert(roconDescription.getMasterId());
            uri = new URI(roconDescription.getMasterId().getMasterUri());
            Log.i(TAG, "init(Intent) - master uri is " + uri.toString());
        } catch (ClassCastException e) {
            Log.e(TAG, "Cannot get concert description from intent. " + e.getMessage());
            throw new RosRuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RosRuntimeException(e);
        }
        nodeMainExecutorService.setMasterUri(uri);
        if (roconDescription.getCurrentRole() == null) {
            chooseRole();
        } else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    while (!validatedConcert) {
                        // should use a sleep here to avoid burnout
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.i(TAG, "init(Intent) passing control back to init(nodeMainExecutorService)");
                    MainActivity.this.init(nodeMainExecutorService);
                    return null;
                }
            }.execute();
        }
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        Log.e(TAG,"init(NodeMainExecutor nodeMainExecutor)");
        try {
            java.net.Socket socket = new java.net.Socket(getMasterUri().getHost(), getMasterUri().getPort());
            java.net.InetAddress local_network_address = socket.getLocalAddress();
            socket.close();
            NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(local_network_address.getHostAddress(), getMasterUri());
            interactionsManager.init(roconDescription.getInteractionsNamespace());
            interactionsManager.getAppsForRole(roconDescription.getMasterId(), roconDescription.getCurrentRole());
            interactionsManager.setRemoconName(statusPublisher.REMOCON_FULL_NAME);
            //execution of publisher
            if (! statusPublisher.isInitialized()) {
                // If we come back from an app, it should be already initialized, so call execute again would crash
                nodeMainExecutorService.execute(statusPublisher, nodeConfiguration.setNodeName(StatusPublisher.NODE_NAME));
            }
            //execution of subscriber
            pairSubscriber.setAppHash(0);

            if (! pairSubscriber.isInitialized()) {
                // If we come back from an app, it should be already initialized, so call execute again would crash
                nodeMainExecutorService.execute(pairSubscriber, nodeConfiguration.setNodeName(pairSubscriber.NODE_NAME));
            }
        } catch (IOException e) {
            // Socket problem
        }
    }

    @Override
    public void startMasterChooser() {
        Log.e(TAG,"开始执行MasterChooserService");
//        startService(new Intent(this, MasterChooserService.class));
    }

    protected void updateAppList(final ArrayList<Interaction> apps, final String master_name, final String role) {
        selectedInteraction = null;
        for (int i=0;i<apps.size();i++){
            Log.e(TAG, "InteractionDisplayName:"+apps.get(i).getDisplayName());
            if (apps.get(i).getDisplayName().equals("Random Walker")){
                selectedInteraction = apps.get(i);
                interactionsManager.requestAppUse(roconDescription.getMasterId(), role, selectedInteraction);
                statusPublisher.update(true, selectedInteraction.getHash(), selectedInteraction.getName());
                try {
                    Thread.sleep(20000);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }finally {
                    statusPublisher.update(false, selectedInteraction.getHash(), selectedInteraction.getName());
                }
            }
        }
    }



    public void validateConcert(final MasterId id) {
        // Run a set of checkers in series. The last step must ensure the master is up.
        final ConcertChecker cc = new ConcertChecker(
                new ConcertChecker.ConcertDescriptionReceiver() {
                    public void receive(RoconDescription concertDescription) {
                        // Check that it's not busy
                        if ( concertDescription.getConnectionStatus() == RoconDescription.UNAVAILABLE ) {
                            Log.e(TAG,"Concert is unavailable : busy serving another remote controller.");
                            startMasterChooser();
                        } else {
                            validatedConcert = true;   // for us this is enough check!
                        }
                    }
                }, new ConcertChecker.FailureHandler() {
            public void handleFailure(String reason) {
                final String reason2 = reason;
                // Kill the connecting to ros master dialog.
                Log.e(TAG,"Cannot contact ROS master: " + reason2);
                // TODO : gracefully abort back to the concert master chooser instead.
                finish();
            }
        });

        // Ensure that the correct WiFi network is selected.
        final WifiChecker wc = new WifiChecker(
                new WifiChecker.SuccessHandler() {
                    public void handleSuccess() {
                        Log.e(TAG,"Starting connection process");
                        cc.beginChecking(id);
                    }
                }, new WifiChecker.FailureHandler() {
            public void handleFailure(String reason) {
                final String reason2 = reason;
                Log.e(TAG,"Cannot connect to concert WiFi: " + reason2);
                finish();
            }
        }, new WifiChecker.ReconnectionHandler() {
            public boolean doReconnection(String from, String to) {
                if (from == null) {
                    Log.e(TAG,"To interact with this master, you must connect to " + to + "\nDo you want to connect to " + to + "?");
                } else {
                    Log.e(TAG,"To interact with this master, you must switch wifi networks" + "\nDo you want to switch from " + from + " to " + to + "?");
                }
                return true;
            }
        });
        wc.beginChecking(id, (WifiManager) getSystemService(WIFI_SERVICE));
    }

    private void chooseRole() {
        Log.i(TAG, "concert chosen; show choose user role dialog");
        roconDescription.setCurrentRole(0);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                while (!validatedConcert) {
                    // should use a sleep here to avoid burnout
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                MainActivity.this.init(nodeMainExecutorService);
                return null;
            }
        }.execute();
    }
}
