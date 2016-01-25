package solar.blaz.date.week;

import android.app.Activity;
import android.os.Bundle;

import org.threeten.bp.LocalDate;

import solar.blaz.date.week.example.R;

/**
 * Created by Blaz Solar on 23/01/16.
 */
public class MainActivity extends Activity {

    private WeekDatePicker datePicker;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        datePicker = (WeekDatePicker) findViewById(R.id.date_picker);

        datePicker.setDateIndicator(LocalDate.now().plusDays(1), true);
    }
}
