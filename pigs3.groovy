enum Material { STRAW, STICKS, BRICKS, VIBRANIUM }

import static Material.*

record House(Material material) { }

interface Eater {
    void eat(House h)
}

@interface Sneaky {}

trait HuffsAndPuffs { /* COMMENT */
    void threaten() {
        println "I'll huff and puff and blow ${ 3 + 5 ** 2 } your house down!"
    }
}

// here is a comment
@Sneaky
class Wolf implements HuffsAndPuffs, Eater {
    void eat(House h) {
        if (h.material < BRICKS) println "The wolf gobbles them up!"
        else println 'The wolf remains hungry.'
    }
}

wolf = new Wolf()
housePiggy4 = new House(VIBRANIUM)
wolf.threaten()
wolf.eat(housePiggy4)
