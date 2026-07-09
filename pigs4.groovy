enum Material { STRAW, STICKS, BRICKS, VIBRANIUM }

import static Material.*

record House(Material material) { }

interface Eater {
  void eat(House h)
}

@interface Sneaky {}

trait HuffsAndPuffs {
  void threaten() {
    println "I'll huff and puff and blow your house down!"
  }
}

@Sneaky
class Wolf implements HuffsAndPuffs, Eater {
  void eat(House h) {
    if (h.material < BRICKS) println "The wolf gobbles them up!"
    else println "The wolf remains hungry."
  }
}