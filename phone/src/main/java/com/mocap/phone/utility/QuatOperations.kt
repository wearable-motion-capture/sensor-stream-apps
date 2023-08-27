package com.mocap.phone.utility

import kotlin.math.sqrt

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

//    private fun quatDiff(a: FloatArray, b: FloatArray): FloatArray {
//        // get the conjugate
//        val aI = floatArrayOf(a[0], -a[1], -a[2], -a[3])
//        // Hamilton product as H(A,B)
//        val hab = floatArrayOf(
//            aI[0] * b[0] - aI[1] * b[1] - aI[2] * b[2] - aI[3] * b[3],
//            aI[0] * b[1] + aI[1] * b[0] + aI[2] * b[3] - aI[3] * b[2],
//            aI[0] * b[2] - aI[1] * b[3] + aI[2] * b[0] + aI[3] * b[1],
//            aI[0] * b[3] + aI[1] * b[2] - aI[2] * b[1] + aI[3] * b[0]
//        )
//        return hab
//    }