package tech.nextcash.nextcashwallet;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;


public class SettingsActivity extends AppCompatActivity
  implements AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener
{
    private static String logTag = "SettingsActivity";

    @Override
    protected void onCreate(Bundle pSavedInstanceState)
    {
        super.onCreate(pSavedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setupActionBar();

        ((TextView)findViewById(R.id.nodeUserAgentValue)).setText(Bitcoin.userAgent());
        ((TextView)findViewById(R.id.networkValue)).setText(Bitcoin.networkName());

        Settings settings = Settings.getInstance(getFilesDir());

        // Configure sync frequency options
        int currentFrequency = settings.intValue("sync_frequency");
        if(currentFrequency == 0)
            currentFrequency = 60; // Default to 60 minutes

        Spinner syncFrequency = findViewById(R.id.syncFrequencySpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
          R.array.sync_frequency_titles, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        syncFrequency.setAdapter(adapter);
        syncFrequency.setOnItemSelectedListener(this);

        boolean found = false;
        int[] frequencyValues = getResources().getIntArray(R.array.sync_frequency_values);
        for(int i=0;i<frequencyValues.length;i++)
            if(frequencyValues[i] == currentFrequency)
            {
                found = true;
                syncFrequency.setSelection(i);
                break;
            }

        if(!found)
            syncFrequency.setSelection(2); // Default to 60 minutes

        // Configure transaction notifications toggle
        Switch notifyTransactions = findViewById(R.id.notifyTransactionsToggle);
        notifyTransactions.setOnCheckedChangeListener(this);
        if(settings.containsValue("notify_transactions"))
            notifyTransactions.setChecked(settings.boolValue("notify_transactions"));
        else
            notifyTransactions.setChecked(false);
    }

    private void setupActionBar()
    {
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_settings_black_24dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_settings));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }
    }

    public void onItemSelected(AdapterView<?> pParent, View pView, int pPos, long pID)
    {
        int[] frequencyValues = getResources().getIntArray(R.array.sync_frequency_values);

        if(pPos >= frequencyValues.length)
            Log.e(logTag, String.format("Invalid sync frequency position selected : %d", pPos));
        else
        {
            Settings.getInstance(getFilesDir()).setIntValue("sync_frequency", frequencyValues[pPos]);
            setResult(1);
            if(frequencyValues[pPos] == -1)
                Log.i(logTag, "Sync frequency set to never.");
            else if(frequencyValues[pPos] >= 60)
                Log.i(logTag, String.format("Sync frequency set to %d hours.", frequencyValues[pPos] / 60));
            else
                Log.i(logTag, String.format("Sync frequency set to %d minutes.", frequencyValues[pPos]));
        }
    }

    public void onNothingSelected(AdapterView<?> pParent)
    {
    }

    @Override
    public void onCheckedChanged(CompoundButton pButtonView, boolean pIsChecked)
    {
        if(pButtonView.getId() == R.id.notifyTransactionsToggle)
        {
            Settings.getInstance(getFilesDir()).setBoolValue("notify_transactions", pIsChecked);
            if(pIsChecked)
                Log.i(logTag, "Transaction notifications turned on");
            else
                Log.i(logTag, "Transaction notifications turned off");
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture)
    {

    }
}
