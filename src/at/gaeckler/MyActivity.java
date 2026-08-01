/*
		Project:		Android Utils
		Module:			MyActivity.java
		Description:	The android activity base for all my android apps
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

package at.gaeckler;

import android.app.AlertDialog;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;

public class MyActivity extends AppCompatActivity
{
	public interface DialogCallback
	{
		void onConfirmed(boolean confirmed);
	}

	/**
	 * Shows a message (alert) dialog
	 * @param iconId the icon to display
	 * @param title the title of the alert
	 * @param message the message to show
	 * @param terminate if true, the activity will be finished after clicking OK (This is useful for
	 *                  displaying an error before terminating)
	 * @param callback You can pass a callback. In this case, a cancel button will be displayed, too.
	 */
	public void showMessage(@DrawableRes int iconId, String title, String message, final boolean terminate, DialogCallback callback )
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
				.setTitle(title)
				.setCancelable(false)
				.setPositiveButton("OK", (dialog, id) ->
				{
					dialog.dismiss();
					if (terminate)
					{
						finish();
					}
					if (callback != null)
					{
						callback.onConfirmed(true);
					}
				})
				.setIcon(iconId)
		;
		if( callback != null )
		{
			builder.setNegativeButton("Abbruch", (dialog, id) ->
			{
				dialog.cancel();
				callback.onConfirmed(false);
			});
		}
		AlertDialog alert = builder.create();
		alert.show();
	}
}
