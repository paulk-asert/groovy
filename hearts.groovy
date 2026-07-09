/grab org.apache.commons:commons-collections4:4.5.0

import org.apache.commons.collections4.bidimap.TreeBidiMap as BMap

class MyMap extends BMap {
  BMap bitwiseNegate() {
    inverseBidiMap()
  }
}

def hearts = [blue: '💙', green: '💚', yellow: '💛', orange: '🧡', purple: '💜'] as MyMap

/prnt $hearts

/prnt ${~hearts}

/pipe |size '.findAll{ it.key.size()' '}'

hearts |size < 6

hearts |size > 5

hearts |size == 4

/classloader

