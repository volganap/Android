package ru.volganap.nikolay.kids_monitor_ably;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttHelper implements KM_Constants{
    public MqttAndroidClient mqttAndroidClient;
    //final String serverUri = "tcp://185.87.48.105:1883";
    //final String username1 = "km_user1";
    //final String password1 = "btbinr12";
    //final String username2 = "km_user2";
    //final String password2 = "btbinr98";
    //final private String clientId;
    private final String subscriptionTopic;
    private final String serverUri;
    private final String username1;
    private final String password1;

    public MqttHelper(Context context, String subscriptionTopic, String clientId){
        this.subscriptionTopic = subscriptionTopic;
        //this.clientId = clientId;

        serverUri = context.getResources().getString(R.string.serverUri);
        username1 = context.getResources().getString(R.string.username1);
        password1 = context.getResources().getString(R.string.password1);

        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {
                Log.d(LOG_TAG, "MqttHelper * connectComplete: "+ s);
            }

            @Override
            public void connectionLost(Throwable throwable) {
                Log.d(LOG_TAG, "MqttHelper * connection Lost: "+ throwable.toString());
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.d(LOG_TAG,"MqttHelper * messageArrived: "+ mqttMessage.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                Log.d(LOG_TAG, "MqttHelper * deliveryComplete");
            }
        });
        connect();
    }

    public void setCallback(MqttCallbackExtended callback) {
        mqttAndroidClient.setCallback(callback);
    }

    private void connect(){
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName(username1);
        mqttConnectOptions.setPassword(password1.toCharArray());
        //mqttConnectOptions.setKeepAliveInterval(MqttConfig.KEEPALIVE);
        //mqttConnectOptions.setMaxInflight(1024);

        try {

            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                    Log.d(LOG_TAG, "MqttHelper * mqttAndroidClient.connect * Success");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(LOG_TAG,"MqttHelper * "+ "Failed to connect to: " + serverUri + "* "+ exception.toString());
                }
            });

        } catch (MqttException ex){
            ex.printStackTrace();
        }
    }

    private void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(LOG_TAG,"MqttHelper * "+"Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(LOG_TAG,"MqttHelper * "+ "Subscribed fail!");
                }
            });

        } catch (MqttException ex) {
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }
}
