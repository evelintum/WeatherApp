package com.evelin.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.wearherapp.R
import com.evelin.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var progressDialog: Dialog? = null
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provides is turned off.Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        requestLocationData()
                    }
                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "You have denied location permission. Please enable them as it is mandatory for the app to work.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        fusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) run {

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if (response.isSuccessful) {
                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Response Result", "$weatherList")
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = sharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("Error 400", "Bad connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }

                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errrrrrr", t.message.toString())
                    hideProgressDialog()
                }
            })

        } else {
            if (Constants.isNetworkAvailable(this)) {
                Toast.makeText(
                    this@MainActivity,
                    "No internet connection available.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") {
                    dialog, _ -> dialog.dismiss()
            }.show()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showCustomProgressDialog() {
        progressDialog = Dialog(this)
        progressDialog?.setContentView(R.layout.dialog_custom_progess)
        progressDialog?.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog?.dismiss()
        }
    }

    private fun setupUI() {

        val weatherResponseJsonString =
            sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!weatherResponseJsonString.isNullOrEmpty()) {

            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            val current = resources.configuration.locale
            for (i in weatherList.weather.indices) {
                Log.i("Weather Name", weatherList.weather.toString())

                findViewById<TextView>(R.id.tv_main).text = weatherList.weather[i].main
                findViewById<TextView>(R.id.tv_main_description).text =
                    weatherList.weather[i].description
                weatherList.main.temp.toString() + getUnit(current.toString())

                findViewById<TextView>(R.id.tv_temp).text = roundToDecimalPlaces(0, weatherList.main.temp).toString() + getUnit(current.toString())
                findViewById<TextView>(R.id.tv_humidity).text =
                    weatherList.main.humidity.toString() + getString(R.string.per_cent)
                findViewById<TextView>(R.id.tv_min).text =
                    roundToDecimalPlaces(1, weatherList.main.temp_min).toString() + " min"
                findViewById<TextView>(R.id.tv_max).text =
                    roundToDecimalPlaces(1, weatherList.main.temp_max).toString() + " max"
                findViewById<TextView>(R.id.tv_speed).text =
                    roundToDecimalPlaces(1, weatherList.wind.speed).toString()
                findViewById<TextView>(R.id.tv_name).text = weatherList.name
                findViewById<TextView>(R.id.tv_country).text = weatherList.sys.country
                findViewById<TextView>(R.id.tv_sunrise_time).text =
                    unixTime(weatherList.sys.sunrise)
                findViewById<TextView>(R.id.tv_sunset_time).text = unixTime(weatherList.sys.sunset)

                var imageView = findViewById<ImageView>(R.id.iv_main)
                when (weatherList.weather[i].icon) {
                    "01d" -> imageView.setImageResource(R.drawable.sunny)
                    "02d" -> imageView.setImageResource(R.drawable.cloud)
                    "03d" -> imageView.setImageResource(R.drawable.cloud)
                    "04d" -> imageView.setImageResource(R.drawable.cloud)
                    "04n" -> imageView.setImageResource(R.drawable.cloud)
                    "10d" -> imageView.setImageResource(R.drawable.rain)
                    "11d" -> imageView.setImageResource(R.drawable.storm)
                    "13d" -> imageView.setImageResource(R.drawable.snowflake)
                    "01n" -> imageView.setImageResource(R.drawable.cloud)
                    "02n" -> imageView.setImageResource(R.drawable.cloud)
                    "03n" -> imageView.setImageResource(R.drawable.cloud)
                    "10n" -> imageView.setImageResource(R.drawable.cloud)
                    "11n" -> imageView.setImageResource(R.drawable.rain)
                    "13n" -> imageView.setImageResource(R.drawable.snowflake)
                }
            }

        }

    }

    private fun roundToDecimalPlaces(decimalPlaces: Int, value: Double): BigDecimal {
        return value.toBigDecimal().setScale(decimalPlaces, RoundingMode.UP)
    }

    private fun getUnit(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}







