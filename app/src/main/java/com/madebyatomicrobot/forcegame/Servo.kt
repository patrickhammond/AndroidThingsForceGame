package com.madebyatomicrobot.walker.robot

import com.madebyatomicrobot.things.drivers.PCA9685

class Servo(
        private val pca9685: PCA9685,
        private val channel: Int,
        private val minPulseDuration: Float = 0.5F,
        private val maxPulseDuration: Float = 2.5F,
        private val softwareMinAngle: Float = 0.0F,
        private val softwareMaxAngle: Float = 180.0F,
        private val physicalMin: Float = 20.0F,
        private val physicalMax: Float = 160.0F) {

    fun moveToAngle(angle: Float) {
        val range = (softwareMaxAngle - softwareMinAngle)
        var realAngle = angle

        if (realAngle < physicalMin) {
            realAngle = physicalMin
        }

        if (realAngle > physicalMax) {
            realAngle = physicalMax
        }

        val percentOfRange = (realAngle - softwareMinAngle) / range  // FIXME need to be smarter about softwareMinAngle when not zero
        val pw = ((minPulseDuration + (maxPulseDuration - minPulseDuration) * percentOfRange) * 1000)
        pca9685.setPwm(channel, 0.0F, pw)
    }

    fun turnOff() {
        pca9685.setPwm(channel, 0, 4096)  // FIXME Magic number for off
    }
}