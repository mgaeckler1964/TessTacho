/*
		Project:		TessTacho
		Module:			TachoWidget.java
		Description:	The widget for the tacho meter
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

import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.util.AttributeSet;
import android.view.View;

class TachoPos
{
	public double xPos, yPos;
	
	TachoPos( double newX, double newY )
	{
		xPos = newX;
		yPos = newY;
	}
}

public class TachoWidget extends View
{
	private int m_tachoWidth = 0, m_tachoHeight = 0;
	private double m_centerX = 0, m_centerY = 0, m_tachoRadius = 0;
	private double m_currentSpeed = 0;
	private double m_currentAccel = 0;
	private boolean m_hasBrakeInfo = false;
	private double m_maxTachoSpeed = 0;
	private double m_maxSpeed = 0;
	private double m_tachoIncrement = 0;
	private int m_totalDistance = 0;
	private double m_dayDistance = 0;
	private Paint m_tachoPaint = null;
	private Paint m_labelPaint = null;
	private Paint m_needlePaint = null;
	private Paint m_speedPaint = null;
	private static final double s_circleAngle = 2*Math.PI; 
	private static final double s_gapAngle = s_circleAngle/4;
	private static final double s_maxAngle = s_circleAngle - s_gapAngle;
	private static final double s_minAngle = (3*s_circleAngle)/4 - s_gapAngle/2; 

	public static final DecimalFormat s_speedFormat = new DecimalFormat( "0.0 km/h" );
	private static final DecimalFormat s_totalDistanceFormat = new DecimalFormat( ",##0" );
	public static final DecimalFormat s_dayDistanceFormat = new DecimalFormat( "0.0" );
	public static final DecimalFormat s_accelFormat = new DecimalFormat( "0.0 m/s²" );

	private static final int blackColor = 0xFF000000;
	private static final int whiteColor = 0xFFFFFFFF;
	private static int s_backGroundCol = whiteColor;
	private static int s_foreGroundCol = blackColor;

	public void setMaxTachoSpeed( double newSpeed )
	{
		if( newSpeed <= 20 )
			m_tachoIncrement = 1;
		else if( newSpeed <= 40 )
			m_tachoIncrement = 2;
		else if( newSpeed <= 100 )
			m_tachoIncrement = 5;
		else if( newSpeed <= 200 )
			m_tachoIncrement = 10;
		else
			m_tachoIncrement = 20;

		m_maxTachoSpeed = newSpeed;
		if( ((int)newSpeed % (int)m_tachoIncrement) != 0 )
			m_maxTachoSpeed = (((int)newSpeed / (int)m_tachoIncrement)+1) * m_tachoIncrement;
		else
			m_maxTachoSpeed = newSpeed;
	}
	public double getMaxTachoSpeed( )
	{
		return m_maxTachoSpeed;
	}
	public double getMaxSpeed( )
	{
		return m_maxSpeed;
	}
	public void setMaxSpeed( double maxSpeed )
	{
		m_maxSpeed = maxSpeed;
	}
	private void initTacho()
	{
		setBackgroundColor(s_backGroundCol);

		m_tachoPaint = new Paint();
		m_tachoPaint.setColor( s_foreGroundCol );
		m_tachoPaint.setStyle(Paint.Style.STROKE);
		m_tachoPaint.setTextAlign(Paint.Align.CENTER);
		m_tachoPaint.setAntiAlias( true );

		m_labelPaint = new Paint();
		m_labelPaint.set( m_tachoPaint );
		m_labelPaint.setStyle(Paint.Style.FILL);

		m_needlePaint = new Paint();
		m_needlePaint.set( m_tachoPaint );
		m_needlePaint.setStrokeWidth(20);
		m_needlePaint.setStrokeCap(Cap.ROUND);

		m_speedPaint = new Paint();
		m_speedPaint.set( m_tachoPaint );
		m_speedPaint.setStyle(Paint.Style.FILL);

		setMaxTachoSpeed( 10 );
	}

	private double getAngleForSpeed( double speed )
	{
		final double angle = s_minAngle - speed*s_maxAngle / m_maxTachoSpeed;
		
		return angle;
	}
	private TachoPos getCirclePosForSpeed( double speed )
	{
		double angle = getAngleForSpeed( speed );
		
		TachoPos pos = new TachoPos( Math.cos( angle ), Math.sin( angle ));
		
		return pos;
	}
	private TachoPos transferToScreen( TachoPos pos, double factor )
	{
		factor *= m_tachoRadius;
		
		pos.xPos *= factor;
		pos.yPos *= factor;
		
		pos.xPos += m_centerX;
		pos.yPos += m_centerY;
		
		pos.yPos = m_tachoHeight-pos.yPos;
		
		return pos;
	}
	private TachoPos getCirclePosForSpeed( double speed, double factor )
	{
		TachoPos pos = getCirclePosForSpeed( speed );
		pos = transferToScreen( pos, factor );

		return pos;
	}
	public TachoWidget(Context context)
	{
		super(context);
		initTacho();
	}
    public TachoWidget(Context context, AttributeSet attrs) 
    {
        super(context, attrs);
		initTacho();
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
    	m_tachoWidth = MeasureSpec.getSize(widthMeasureSpec);
    	m_tachoHeight = MeasureSpec.getSize(heightMeasureSpec);
    	m_centerX = m_tachoWidth/2;
    	m_centerY = m_tachoHeight/2;
    	m_tachoRadius = Math.min( m_centerX, m_centerY);
    	
    	m_labelPaint.setTextSize((float)(m_tachoRadius * 0.1));
    	m_speedPaint.setTextSize((float)(m_tachoRadius * 0.25));

        setMeasuredDimension( m_tachoWidth, m_tachoHeight );
    }
	@Override
    protected void onDraw(Canvas canvas)
    {
		double speed;
		double textOffset = m_labelPaint.getTextSize()/2.0;
        super.onDraw(canvas);
        // canvas.drawLine( 0, 0, (float)centerX, (float)centerY, circlePaint);
        canvas.drawCircle( (float)m_centerX, (float)m_centerY, (float)m_tachoRadius, m_tachoPaint);
        for( speed = 0; speed <= m_maxTachoSpeed; speed += m_tachoIncrement )
        {
        	TachoPos outerPos = getCirclePosForSpeed( speed, 1.0 );
        	TachoPos innerPos = getCirclePosForSpeed( speed, 0.9 );
        	TachoPos textPos = getCirclePosForSpeed( speed, 0.8 );
        	
        	canvas.drawLine( 
        		(float)outerPos.xPos, (float)outerPos.yPos, 
        		(float)innerPos.xPos, (float)innerPos.yPos, 
        		m_tachoPaint
        	);
        	canvas.drawText(
        		Integer.toString((int)speed), 
        		(float)textPos.xPos, (float)(textPos.yPos+textOffset), 
        		m_labelPaint 
        	);
        }

    	TachoPos needlePos = getCirclePosForSpeed( m_currentSpeed, 0.7 );
    	canvas.drawLine( 
        	(float)needlePos.xPos, (float)needlePos.yPos, 
        	(float)m_centerX, (float)m_centerY, 
        	m_needlePaint
        );
    	
    	textOffset = m_speedPaint.getTextSize();
    	canvas.drawText(s_speedFormat.format(m_currentSpeed), (float)m_centerX, (float)(m_centerY+textOffset), m_speedPaint);

    	textOffset += m_labelPaint.getTextSize();
//    	canvas.drawText(Integer.toString( totalDistance ), (float)centerX, (float)(centerY+textOffset), tachoPaint);
    	canvas.drawText(s_totalDistanceFormat.format(m_totalDistance), (float)m_centerX, (float)(m_centerY+textOffset), m_labelPaint);

    	textOffset += m_labelPaint.getTextSize();
    	canvas.drawText(s_dayDistanceFormat.format(m_dayDistance), (float)m_centerX, (float)(m_centerY+textOffset), m_labelPaint);

    	textOffset += m_labelPaint.getTextSize();
    	canvas.drawText((m_hasBrakeInfo?"*":" ") + s_accelFormat.format(m_currentAccel), (float)m_centerX, (float)(m_centerY+textOffset), m_labelPaint);
    }
	public void showSpeedAndDistance( double speed, double accel, int total, double dayDistance, boolean hasBrakeInfo )
	{
		m_currentSpeed = speed;
		if( speed > m_maxSpeed)
		{
			m_maxSpeed = speed;
			if( speed > m_maxTachoSpeed )
				setMaxTachoSpeed( speed );
		}
		m_currentAccel = accel;
		
		m_totalDistance = total;
		m_dayDistance = dayDistance;
		m_hasBrakeInfo = hasBrakeInfo;
		invalidate();
	}

	public void useBlackBackground()
	{
		s_backGroundCol = blackColor;
		s_foreGroundCol = whiteColor;
		initTacho();
		invalidate();
	}
	public void useWhiteBackground()
	{
		s_backGroundCol = whiteColor;
		s_foreGroundCol = blackColor;
		initTacho();
		invalidate();
	}
}
