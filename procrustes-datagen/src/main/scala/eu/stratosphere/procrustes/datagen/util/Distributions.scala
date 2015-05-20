package eu.stratosphere.procrustes.datagen.util

import eu.stratosphere.procrustes.datagen.util.RanHash

object Distributions {

  trait Distribution {
    def sample(rand: RanHash): Double
  }

  case class Gaussian(mu: Double, sigma: Double) extends Distribution {
    def sample(rand: RanHash) = {
      sigma * rand.nextGaussian()
    }
  }

  case class Uniform(k: Int) extends Distribution {
    def sample(rand: RanHash) = {
      rand.nextInt(k)
    }
  }

  case class Pareto(a: Double) extends Distribution {
    def sample(rand: RanHash) = {
      Math.round(rand.nextPareto(a))
    }
  }
}
