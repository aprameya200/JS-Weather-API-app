package com.example.api_to_database

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.api_to_database.databinding.ActivityMainBinding
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.await
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recyclerView: RecyclerView

    private var cache: CacheMemory = CacheMemory(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        // Use lifecycleScope to launch the coroutine
        lifecycleScope.launch {
            val weatherData = getWeather()

            // Now that getWeather() is completed, proceed with the next steps
            val mainDataList = getAllMainData()
            recyclerView.adapter = MyAdapter(this@MainActivity, mainDataList, getAllData())
        }
    }

    private suspend fun getAllData(): List<WeatherData> {
        val weatherDB = WeatherDatabase.getDatabase(this)
        return withContext(Dispatchers.IO) {
            weatherDB.weatherDao().getAllWeatherData()
        }
    }

    private suspend fun getAllMainData(): List<Main> {
        val weatherDB = WeatherDatabase.getDatabase(this)
        return withContext(Dispatchers.IO) {
            weatherDB.weatherDao().getAllMain()
        }
    }

    private suspend fun getWeather(): WeatherResponse? {
        val weatherService = WeatherService.weatherInstance

        // Fetch weather forecast
        val weatherCall = weatherService.getWeatherForecast("Kathmandu", "metric")
        val weatherResponse = weatherCall.await()

        if (weatherResponse.isSuccessful) {
            val weatherData = weatherResponse.body()
            weatherData?.let {
                val weatherDB = WeatherDatabase.getDatabase(this)
                withContext(Dispatchers.IO) {
                    weatherDB.weatherDao().insertAll(it)
                    for (item in it.list) {
                        weatherDB.weatherDao().insertWeatherData(item)
                        weatherDB.weatherDao().insertMain(item.main)
                        weatherDB.weatherDao().insertWeather(item.weather[0])
                        weatherDB.weatherDao().insertClouds(item.clouds)
                        weatherDB.weatherDao().insertWinds(item.wind)
                        weatherDB.weatherDao().insertSys(item.sys)
                    }
                }
            }
        } else {
            Log.d("FAILURE", "Error in fetching weather data")
        }

        // Fetch current weather
        val currentWeatherCall = weatherService.getCurrentWeather("Kathmandu", "metric")
        val currentWeatherResponse = currentWeatherCall.await()

        if (currentWeatherResponse.isSuccessful) {
            val weather = currentWeatherResponse.body()
            weather?.let {
                binding.apply {
                    weatherIn.text = it.name.toString()
                    temperature.text = "${it.main.temp.toInt()}°"
                    description.text = it.weather[0].main.toString()
                    high.text = "Max: ${it.main.temp_max}°"
                    low.text = "Min: ${it.main.temp_min}°"

                    val icon = it.weather[0].icon
                    val imageUrl = "http://openweathermap.org/img/wn/$icon@2x.png"
                    Glide.with(this@MainActivity).load(imageUrl).into(iconImage)
                }
                cache.put("Current Weather", Gson().toJson(it))
            }
        } else {
            Log.d("FAILURE", "Error in fetching current weather data")
        }

        return null
    }
}
