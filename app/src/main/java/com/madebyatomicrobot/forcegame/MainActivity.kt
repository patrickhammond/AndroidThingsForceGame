package com.madebyatomicrobot.forcegame

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.things.pio.I2cDevice
import com.google.android.things.pio.PeripheralManagerService
import com.madebyatomicrobot.things.drivers.ADS1015
import com.madebyatomicrobot.things.drivers.PCA9685
import com.madebyatomicrobot.walker.robot.Servo

class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG: String = MainActivity::class.java.simpleName

        private val MAX_FSR = 1354 //2047
    }

    private val handler = Handler()
    private val gameStateRunnable = GameStateRunnable()
    private val countdownRunnable = CountdownRunnable()

    private lateinit var i2cServos: I2cDevice
    private lateinit var i2cADC: I2cDevice

    private lateinit var pca9685: PCA9685
    private lateinit var servo: Servo
    private lateinit var ads2015: ADS1015

    private lateinit var redIndicatorView: View
    private lateinit var greenIndicatorView: View
    private lateinit var redScoreView: TextView
    private lateinit var greenScoreView: TextView
    private lateinit var gameActionView: Button

    private var gameState = GameState.READY
    private var redScore: Int = 0
    private var greenScore: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = PeripheralManagerService()
        Log.i(TAG, "I2C: " + manager.i2cBusList)
        i2cServos = manager.openI2cDevice("I2C1", 0x40)  // 0x40 is the default PCA9685 address
        pca9685 = PCA9685(i2cServos)
        pca9685.resetI2C()
        pca9685.setPWMFreq(50.0)  // 50 Hz
        servo = Servo(pca9685, channel = 0)

        i2cADC = manager.openI2cDevice("I2C1", 0x48)
        ads2015 = ADS1015(i2cADC)

        setContentView(R.layout.activity_main)
        redIndicatorView = findViewById(R.id.red_indicator)
        greenIndicatorView = findViewById(R.id.green_indicator)
        redScoreView = findViewById(R.id.red_score) as TextView
        greenScoreView = findViewById(R.id.green_score) as TextView
        gameActionView = findViewById(R.id.game_action) as Button

        gameActionView.setOnClickListener { handleGameAction() }

        gameStateRunnable.progressGameLoop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(countdownRunnable)
        handler.removeCallbacks(gameStateRunnable)

        i2cServos.close()
        i2cADC.close()

        super.onDestroy()
    }

    private fun updateGameDisplay() {
        when(gameState) {
            GameState.READY -> {
                resetBell()

                redIndicatorView.layoutParams = buildNewIndicatorWeight(0)
                greenIndicatorView.layoutParams = buildNewIndicatorWeight(0)

                redScoreView.text = "0"
                greenScoreView.text = "0"

                gameActionView.text = "Start"
                gameActionView.visibility = View.VISIBLE
            }

            GameState.STARTING -> {
                redIndicatorView.layoutParams = buildNewIndicatorWeight(0)
                greenIndicatorView.layoutParams = buildNewIndicatorWeight(0)

                redScoreView.text = "0"
                greenScoreView.text = "0"

                gameActionView.visibility = View.VISIBLE
            }

            GameState.PLAYING -> {
                redIndicatorView.layoutParams = buildNewIndicatorWeight(redScore)
                greenIndicatorView.layoutParams = buildNewIndicatorWeight(greenScore)

                redScoreView.text = "$redScore"
                greenScoreView.text = "$greenScore"

                gameActionView.visibility = View.GONE
            }

            GameState.ENDED -> {
                redIndicatorView.layoutParams = buildNewIndicatorWeight(redScore)
                greenIndicatorView.layoutParams = buildNewIndicatorWeight(greenScore)

                redScoreView.text = if (redScore > greenScore) "Winner!" else "Loser!"
                greenScoreView.text = if (greenScore > redScore) "Winner!" else "Loser!"

                gameActionView.text = "Play again?"
                gameActionView.visibility = View.VISIBLE
            }
        }
    }

    private fun handleGameAction() {
        when(gameState) {
            GameState.READY -> startCountdown()
            GameState.ENDED -> restartGame()
            else -> { }
        }
    }

    private fun startCountdown() {
        handler.post(countdownRunnable)
    }

    private fun restartGame() {
        gameState = GameState.READY
    }

    private fun updateScores() {
        val redReading = ads2015.readADC(0)
        val greenReading = ads2015.readADC(1)

        redScore = ((redReading.toFloat() / MAX_FSR.toFloat()) * 100f).toInt()
        greenScore = ((greenReading.toFloat() / MAX_FSR.toFloat()) * 100f).toInt()
    }

    private fun resetBell() {
        servo.moveToAngle(30.0f)
    }

    private fun dingBell() {
        servo.moveToAngle(160.0f)
    }

    private fun buildNewIndicatorWeight(score: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                score.toFloat() / 100.0f)
    }

    private inner class GameStateRunnable : Runnable {
        override fun run() {
            if (GameState.READY == gameState) {
                redScore = 0
                greenScore = 0
            } else if (GameState.PLAYING == gameState) {
                updateScores()
                if (redScore == 100 || greenScore == 100) {
                    gameState = GameState.ENDED
                    dingBell()
                }
            }

            updateGameDisplay()
            progressGameLoop()
        }

        fun progressGameLoop() {
            handler.post(this)
        }
    }

    private inner class CountdownRunnable : Runnable {
        private var secondsRemaining = -1

        override fun run() {
            if (secondsRemaining == 0) {
                secondsRemaining = -1
                gameState = GameState.PLAYING
            } else {
                gameState = GameState.STARTING
                if (secondsRemaining == -1) {
                    secondsRemaining = 5
                }

                gameActionView.text = "$secondsRemaining"
                secondsRemaining--

                tickCountdown()
            }
        }

        private fun tickCountdown() {
            handler.postDelayed(this, 1000)
        }
    }

    private enum class GameState {
        READY,
        STARTING,
        PLAYING,
        ENDED
    }
}
