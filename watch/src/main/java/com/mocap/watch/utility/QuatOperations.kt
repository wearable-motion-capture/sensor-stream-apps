package com.mocap.watch.utility

import kotlin.math.sqrt

/**
 * Estimates rotation around global y-axis (Up) from watch orientation.
 * This corresponds to the azimuth in polar coordinates. It the angle from the z-axis (forward)
 * in between +pi and -pi.
 */
fun getGlobalYRotation(rotVec : FloatArray): Double {
    // smartwatch rotation to [-w,x,z,y]
    val r = floatArrayOf(-rotVec[0], rotVec[1], rotVec[2], rotVec[3])
    val p = floatArrayOf(0f, 0f, 0f, 1f) // forward vector with [0,x,y,z]

    // this is the result of H(R,P)
    val hrp = floatArrayOf(
        r[0] * p[0] - r[1] * p[1] - r[2] * p[2] - r[3] * p[3],
        r[0] * p[1] + r[1] * p[0] + r[2] * p[3] - r[3] * p[2],
        r[0] * p[2] - r[1] * p[3] + r[2] * p[0] + r[3] * p[1],
        r[0] * p[3] + r[1] * p[2] - r[2] * p[1] + r[3] * p[0]
    )

    val r_p = floatArrayOf(r[0], -r[1], -r[2], -r[3]) // this is R'
    // the final H(H(R,P),R')
    val p_p = floatArrayOf(
        hrp[0] * r_p[0] - hrp[1] * r_p[1] - hrp[2] * r_p[2] - hrp[3] * r_p[3],
        hrp[0] * r_p[1] + hrp[1] * r_p[0] + hrp[2] * r_p[3] - hrp[3] * r_p[2],
        hrp[0] * r_p[2] - hrp[1] * r_p[3] + hrp[2] * r_p[0] + hrp[3] * r_p[1],
        hrp[0] * r_p[3] + hrp[1] * r_p[2] - hrp[2] * r_p[1] + hrp[3] * r_p[0]
    )
    // get angle with atan2
    val yRot = kotlin.math.atan2(p_p[1], p_p[3])

    return yRot * 57.29578
}

/**
 * returns the average of a list of quaternions
 */
fun quatAverage(quats: List<FloatArray>): FloatArray {
    val w = 1.0F / quats.count().toFloat()
    val q0 = quats[0] // first quaternion
    val qavg = FloatArray(q0.size) { i -> q0[i] * w }
    for (i in 1 until quats.count()) {
        val qi = quats[i]
        // dot product of qi and q0
        var dot = 0.0F
        for (j in q0.indices) dot += qi[j] * q0[j]
        if (dot < 0.0) {
            // two quaternions can represent the same orientation
            // "flip" them back close to the first quaternion if needed
            for (j in qavg.indices) qavg[j] += qi[j] * -w
        } else {
            // otherwise, just average
            for (j in qavg.indices) qavg[j] += qi[j] * w
        }
    }
    // squared sum of quat
    var sqsum = 0.0F
    for (i in qavg.indices) sqsum += qavg[i] * qavg[i]
    // l2norm
    val l2norm = sqrt(sqsum)
    // normalized, averaged quaternion
    for (i in qavg.indices) qavg[i] /= l2norm
    return qavg
}