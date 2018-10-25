package bt2chat.csm117.bt2chat;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE = "MESSAGE";
    private final static int REQUEST_ENABLE_BLUETOOTH = 1;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --------- CHANGE BELOW

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish(); //automatic close app if Bluetooth service is not available!
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }

        // --------- CHANGE ABOVE
    }

    public void openDialog(View view) {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        List<String> devices = new ArrayList<String>();
        for(BluetoothDevice bt : pairedDevices)
            devices.add(bt.getName() + "\n" + bt.getAddress());

        ArrayAdapter adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                devices);

        listView.setAdapter(adapter);

        final Dialog dialog = new Dialog(this); // Context, this, etc.
        dialog.setContentView(R.layout.bt_dialog);
        dialog.setTitle(R.string.dialog_title);
        dialog.show();
    }

}
