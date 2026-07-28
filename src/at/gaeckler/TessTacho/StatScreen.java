/*
		Project:		TessTacho
		Module:			StatScreen.java
		Description:	The status activity for the tacho meter
		Author:			Martin Gäckler
		Address:		Hofmannsthalweg 14, A-4030 Linz
		Web:			https://www.gaeckler.at/

		Copyright:		(c) 2013-2026 Martin Gäckler

		This program is free software: you can redistribute it and/or modify
		it under the terms of the GNU General Public License as published by
		the Free Software Foundation, version 3.

		You should have received a copy of the GNU General Public License
		along with this program. If not, see <http://www.gnu.org/licenses/>.

		THIS SOFTWARE IS PROVIDED BY Martin Gäckler, Linz, Austria ``AS IS''
		AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
		TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
		PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR
		CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
		SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
		LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
		USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
		ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
		OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
		OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
		SUCH DAMAGE.
*/
package at.gaeckler.TessTacho;

import at.gaeckler.TessTacho.R;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class StatScreen extends Activity
{
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
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
        {
        	Button btn = (Button) findViewById( R.id.quitButton );
        	btn.setOnClickListener( new OnClickListener() {
				
				@Override
				public void onClick(View arg0) {
					finish();
				}
			});
        }
	}
}
