/**
 * 
 */
package at.gaeckler.TessTacho;

import at.gaeckler.TessTacho.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * @author gak
 *
 */
public class StatScreen extends Activity {


	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stat_screen);
        
        {
	    	TextView maxSpeedView = (TextView)findViewById( R.id.maxSpeedView );
	        double maxSpeed = getIntent().getDoubleExtra(TessTachoActivity.MAX_SPEED_KEY, 0 );
	        String maxSpeedStr = TachoWidget.s_speedFormat.format(maxSpeed);
	        maxSpeedView.setText(maxSpeedStr);
        }

        {
	    	TextView maxAccelView = (TextView)findViewById( R.id.maxAccelView );
	        double maxAccel = getIntent().getDoubleExtra(TessTachoActivity.MAX_ACCEL_KEY, 0 );
	        String maxAccelStr = TachoWidget.s_speedFormat.format(maxAccel);
	        maxAccelView.setText(maxAccelStr);
        }
        {
	    	TextView maxAccelView = (TextView)findViewById( R.id.maxAccelView );
	        double maxAccel = getIntent().getDoubleExtra(TessTachoActivity.MAX_ACCEL_KEY, 0 );
	        String maxAccelStr = TachoWidget.s_accelFormat.format(maxAccel);
	        maxAccelView.setText(maxAccelStr);
        }
        {
	    	TextView maxBrakeView = (TextView)findViewById( R.id.maxBrakeView );
	        double maxBrake = getIntent().getDoubleExtra(TessTachoActivity.MAX_BRAKE_KEY, 0 );
	        String maxBrakeStr = TachoWidget.s_accelFormat.format(maxBrake);
	        maxBrakeView.setText(maxBrakeStr);
        }
        {
	    	TextView brakeSpeedView = (TextView)findViewById( R.id.brakeSpeedView );
	        double brakeSpeed = getIntent().getDoubleExtra(TessTachoActivity.BRAKE_SPEED_KEY, 0 );
	        String brakeSpeedStr = TachoWidget.s_speedFormat.format(brakeSpeed);
	        brakeSpeedView.setText(brakeSpeedStr);
        }
        {
	    	TextView brakeDistanceView = (TextView)findViewById( R.id.brakeDistanceView );
	        double brakeDistance = getIntent().getDoubleExtra(TessTachoActivity.BRAKE_DISTANCE_KEY, 0 );
	        String brakeDistanceStr = TachoWidget.s_dayDistanceFormat.format(brakeDistance);
	        brakeDistanceView.setText(brakeDistanceStr);
        }
        {
	    	TextView resolutionView = (TextView)findViewById( R.id.resolutionView );
	        double resolution = getIntent().getDoubleExtra(TessTachoActivity.RESOLUTION_KEY, 0 );
	        String resolutionStr = Double.toString(resolution);
	        resolutionView.setText(resolutionStr);
        }
	}

	@Override
    public void onBackPressed()
    {
    	finish();
    	super.onBackPressed();
    }
}
