package com.github.rosjava.android_remocons.rocon_remocon;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import com.github.rosjava.android_remocons.common_tools.master.MasterId;
import com.github.rosjava.android_remocons.common_tools.master.RoconDescription;
import com.github.rosjava.android_remocons.common_tools.zeroconf.DiscoverySetup;
import com.github.rosjava.android_remocons.common_tools.zeroconf.Logger;
import com.github.rosjava.zeroconf_jmdns_suite.jmdns.DiscoveredService;
import com.github.rosjava.zeroconf_jmdns_suite.jmdns.Zeroconf;
import com.github.rosjava.zeroconf_jmdns_suite.jmdns.ZeroconfDiscoveryHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MasterChooserService extends Service {

    private Zeroconf zeroconf;
    private ArrayList<DiscoveredService> discoveredMasters;
    private List<RoconDescription> masters;
    private List<MasterItem> masterItems;
    private DiscoveryResultHandler discoveryHandler;
    private Logger logger;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        discoveredMasters = new ArrayList<DiscoveredService>();
        masters = new ArrayList<RoconDescription>();
        logger = new Logger();
        zeroconf = new Zeroconf(logger);
        discoveryHandler = new DiscoveryResultHandler();
        zeroconf.setDefaultDiscoveryCallback(discoveryHandler);
        new DiscoverySetup().execute(zeroconf);
    }

    private class DiscoveryResultHandler implements ZeroconfDiscoveryHandler {
        /**
         * ******************
         * Tasks
         * ******************
         */
        @SuppressLint("NewApi")
        private class ServiceAddedTask extends AsyncTask<DiscoveredService, String, Void> {

            @SuppressLint("NewApi")
            protected Void doInBackground(DiscoveredService... services) {
                if (services.length == 1) {
                    DiscoveredService service = services[0];
                    String result = "[+] Service added: " + service.name + "." + service.type + "." + service.domain + ".";
                    publishProgress(result);
                } else {
                    publishProgress("Error - ServiceAddedTask::doInBackground received #services != 1");
                }
                return null;
            }

        }

        @SuppressLint("NewApi")
        private class ServiceResolvedTask extends AsyncTask<DiscoveredService, String, DiscoveredService> {

            @SuppressLint("NewApi")
            protected DiscoveredService doInBackground(DiscoveredService... services) {
                if (services.length == 1) {
                    DiscoveredService discovered_service = services[0];
                    String result = "[=] Service resolved: " + discovered_service.name + "." + discovered_service.type + "." + discovered_service.domain + ".\n";
                    result += "    Port: " + discovered_service.port;
                    for (String address : discovered_service.ipv4_addresses) {
                        result += "\n    Address: " + address;
                    }
                    for (String address : discovered_service.ipv6_addresses) {
                        result += "\n    Address: " + address;
                    }
                    publishProgress(result);
                    return discovered_service;
                } else {
                    publishProgress("Error - ServiceAddedTask::doInBackground received #services != 1");
                }
                return null;
            }

            @SuppressLint("NewApi")
            protected void onPostExecute(DiscoveredService discovered_service) {
                // add to the content and notify the list view if its a new service
                if (discovered_service != null) {
                    int index = 0;
                    for (DiscoveredService s : discoveredMasters) {
                        if (s.name.equals(discovered_service.name)) {
                            break;
                        } else {
                            ++index;
                        }
                    }
                    if (index == discoveredMasters.size()) {
                        discoveredMasters.add(discovered_service);
                        android.util.Log.e("MasterChooserService", "size:" + discoveredMasters.size());
                        if (discoveredMasters.size() == 0) {
                            return;
                        }
                        for (int i = 0; i < discoveredMasters.size(); i++) {
                            android.util.Log.e("MasterChooserService", "name:" + discoveredMasters.get(i).name + ",Type:" + discoveredMasters.get(i).type + ",domain:" + discoveredMasters.get(i).domain + ",description:" + discoveredMasters.get(i).description + ",hostname:" + discoveredMasters.get(i).hostname + ",port:" + discoveredMasters.get(i).port + ",ipv4:" + discoveredMasters.get(i).ipv4_addresses);
                            if ("192.168.2.191".equals(discoveredMasters.get(i).ipv4_addresses.get(0))){
                                android.util.Log.e("MasterChooserService","找到：192.168.2.191");
                                enterMasterInfo(discoveredMasters.get(i));
                            }
                        }
//                    discovery_adapter.notifyDataSetChanged();
                    } else {
                        android.util.Log.i("MasterChooserService", "Tried to add an existing service (fix this)");
                    }
                }
            }
        }

        @SuppressLint("NewApi")
        private class ServiceRemovedTask extends AsyncTask<DiscoveredService, String, DiscoveredService> {

            @SuppressLint("NewApi")
            protected DiscoveredService doInBackground(DiscoveredService... services) {
                if (services.length == 1) {
                    DiscoveredService discovered_service = services[0];
                    String result = "[-] Service removed: " + discovered_service.name + "." + discovered_service.type + "." + discovered_service.domain + ".\n";
                    result += "    Port: " + discovered_service.port;
                    publishProgress(result);
                    return discovered_service;
                } else {
                    publishProgress("Error - ServiceAddedTask::doInBackground received #services != 1");
                }
                return null;
            }


            protected void onPostExecute(DiscoveredService discovered_service) {
                // remove service from storage and notify list view
                if (discovered_service != null) {
                    int index = 0;
                    for (DiscoveredService s : discoveredMasters) {
                        if (s.name.equals(discovered_service.name)) {
                            break;
                        } else {
                            ++index;
                        }
                    }
                    if (index != discoveredMasters.size()) {
                        discoveredMasters.remove(index);
                        android.util.Log.e("MasterListSize", "size:" + discoveredMasters.size());
                        if (discoveredMasters.size() == 0) {
                            return;
                        }
                        for (int i = 0; i < discoveredMasters.size(); i++) {
                            android.util.Log.e("MasterListSize", "name:" + discoveredMasters.get(i).name + ",Type:" + discoveredMasters.get(i).type + ",domain:" + discoveredMasters.get(i).domain + ",description:" + discoveredMasters.get(i).description + ",hostname:" + discoveredMasters.get(i).hostname + ",port:" + discoveredMasters.get(i).port + ",ipv4:" + discoveredMasters.get(i).ipv4_addresses);
                        }
//                    discovery_adapter.notifyDataSetChanged();
                    } else {
                        android.util.Log.i("zeroconf", "Tried to remove a non-existant service");
                    }
                }
            }
        }

        /**
         * ******************
         * Callbacks
         * ******************
         */
        public void serviceAdded(DiscoveredService service) {
            new ServiceAddedTask().execute(service);
        }

        public void serviceRemoved(DiscoveredService service) {
            new ServiceRemovedTask().execute(service);
        }

        public void serviceResolved(DiscoveredService service) {
            new ServiceResolvedTask().execute(service);
        }
    }


    public void enterMasterInfo(DiscoveredService discovered_service) {
        /*
          This could be better - it should actually contact and check off each
          resolvable zeroconf address looking for the master. Instead, we just grab
          the first ipv4 address and totally ignore the possibility of an ipv6 master.
         */
        String newMasterUri = null;
        if ( discovered_service.ipv4_addresses.size() != 0 ) {
            newMasterUri = "http://" + discovered_service.ipv4_addresses.get(0) + ":"
                    + discovered_service.port + "/";
            Log.e("MasterChooserService","newMasterUri:"+newMasterUri);
        }
        if (newMasterUri != null && newMasterUri.length() > 0) {
            android.util.Log.i("Remocon", newMasterUri);
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("URL", newMasterUri);
            try {
                addMaster(new MasterId(data));
            } catch (Exception e) {
                Log.e("MasterChooserService","Invalid Parameters.");
            }
        } else {
            Log.e("MasterChooserService","No valid resolvable master URI.");
        }
    }
    private void addMaster(MasterId masterId) {
        addMaster(masterId, false);
    }

    private void addMaster(MasterId masterId, boolean connectToDuplicates) {
        Log.e("MasterChooserService", "adding master to the concert master chooser [" + masterId.toString() + "]");
        if (masterId == null || masterId.getMasterUri() == null) {
        } else {
            for (int i = 0; i < masters.toArray().length; i++) {
                RoconDescription concert = masters.get(i);
                if (concert.getMasterId().equals(masterId)) {
                    if (connectToDuplicates) {
                        choose(i);
                        return;
                    } else {
                        Log.e("MasterChooserService","That concert is already listed.");
                        return;
                    }
                }
            }
            Log.e("MasterChooserService", "creating concert description: " + masterId.toString());
            masters.add(RoconDescription.createUnknown(masterId));
            Log.e("MasterChooserService", "description created");
            onMastersChanged();
        }
    }
    private void onMastersChanged() {
        masterItems = new ArrayList<MasterItem>();
        if (masters != null) {
            for (int i = 0; i < masters.size(); i++) {
                masterItems.add(new MasterItem(masters.get(i), this));
            }
        }
        try {
            Thread.sleep(5000);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            choose(0);
        }
    }


//    private void updateListView() {
//        setContentView(R.layout.master_chooser);
//        ListView listview = (ListView) findViewById(R.id.master_list);
//        listview.setAdapter(new MasterAdapter(this, masters));
//        registerForContextMenu(listview);
//
//        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View v,
//                                    int position, long id) {
//                choose(position);
//            }
//        });
//    }


    /**
     * Called when the user clicks on one of the listed masters in master chooser
     * view. Should probably check the connection status before
     * proceeding here, but perhaps we can just rely on the user clicking
     * refresh so this process stays without any lag delay.
     *
     * @param position
     */
    private void choose(int position) {
        Log.e("MasterChooserService","position:"+position);
        RoconDescription concert = masters.get(position);
        Log.e("MasterChooserService","InteractionsNamespace:"+concert.getInteractionsNamespace());
        if (concert == null || concert.getConnectionStatus() == null || concert.getConnectionStatus().equals(RoconDescription.ERROR)) {
            Log.e("MasterChooserService","Error!,Failed: Cannot contact concert");
        } else if ( concert.getConnectionStatus().equals(RoconDescription.UNAVAILABLE) ) {
            Log.e("MasterChooserService","Master Unavailable!,Currently busy serving another.");
        } else {
            Log.e("MasterChooserService","Master available!,SUCCESS");


            Intent intent=new Intent();
            intent.putExtra("RoconDescription",concert);
            intent.setAction("com.robot.et.rocon");
            sendBroadcast(intent);


//            Intent resultIntent = new Intent();
//            resultIntent.putExtra(RoconDescription.UNIQUE_KEY, concert);
//            setResult(RESULT_OK, resultIntent);
//            finish();
        }
    }
}
