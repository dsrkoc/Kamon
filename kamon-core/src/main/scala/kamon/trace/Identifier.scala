package kamon
package trace

import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

import kamon.util.HexCodec

import scala.util.Try


/**
  * Encapsulates an identifier in its String and Byte representations. Since it is a very common practice to include
  * Trace and (sometimes) Span identifiers on logs we make heavy use of the String representation since Identifiers
  * are assigned even if the Trace is not sampled. The binary representation might be used by some reporters or when
  * transferring the identifiers through binary transports.
  *
  * Users of this class must ensure that the String and Binary representations are equivalent. Most likely you will
  * never want to create an instance by yourself but rather use one of the existent providers (or your own) to create
  * instances.
  */
case class Identifier(string: String, bytes: Array[Byte]) {

  /** Returns true if the identifier does not contain any usable value */
  def isEmpty: Boolean =
    string.isEmpty

  override def equals(obj: Any): Boolean = {
    if(obj != null && obj.isInstanceOf[Identifier])
      obj.asInstanceOf[Identifier].string == string
    else false
  }
}

object Identifier {

  val Empty = Identifier("", new Array[Byte](0))

  /**
    * A Identifier Scheme controls how Trace and Span identifiers are parsed and generated by the tracer and context
    * propagation mechanisms.
    *
    * @param traceIdFactory Factory to be used for the Trace identifiers
    * @param spanIdFactory Factory to be used for the Span identifiers
    */
  case class Scheme (
    traceIdFactory: Factory,
    spanIdFactory: Factory
  )

  object Scheme {

    /**
      * A Identifier Scheme that uses 8 byte identifiers for both Trace and Span ids.
      */
    val Single = Scheme(Factory.EightBytesIdentifier, Factory.EightBytesIdentifier)

    /**
      * A Identifier Scheme that uses 16 byte identifiers for Traces and 8 byte identifiers for Spans.
      */
    val Double = Scheme(Factory.SixteenBytesIdentifier, Factory.EightBytesIdentifier)

  }


  /**
    * Generates random identifiers and parses identifiers from both string and binary representations.
    */
  trait Factory {

    /** Generates a new, random identifier */
    def generate(): Identifier

    /**
      * Parses an identifier from its string representation. If the provided string does not contain a valid identifier
      * then the Empty identifier should be returned instead.
      */
    def from(string: String): Identifier

    /**
      * Parses an identifier from its binary representation. If the provided byte array does not contain a valid
      * identifier then the Empty identifier should be returned instead.
      */
    def from(bytes: Array[Byte]): Identifier
  }


  object Factory {

    /**
      * Generates and parses identifiers with a fixed 8-bytes length. The String representation for these identifiers
      * corresponds to the HEX representation of the binary data.
      */
    val EightBytesIdentifier = new Factory {

      override def generate(): Identifier = {
        val data = ByteBuffer.wrap(new Array[Byte](8))
        val random = ThreadLocalRandom.current().nextLong()
        data.putLong(random)

        Identifier(HexCodec.toLowerHex(random), data.array())
      }

      override def from(string: String): Identifier =  Try {
        val identifierLong = HexCodec.lowerHexToUnsignedLong(string)
        val data = ByteBuffer.allocate(8)
        data.putLong(identifierLong)

        Identifier(string, data.array())
      } getOrElse(Empty)

      override def from(bytes: Array[Byte]): Identifier = Try {
        val buffer = ByteBuffer.wrap(bytes)
        val identifierLong = buffer.getLong

        Identifier(HexCodec.toLowerHex(identifierLong), bytes)
      } getOrElse(Empty)
    }

    /**
      * Generates and parses identifiers with a fixed 16-bytes length. The String representation for these identifiers
      * corresponds to the HEX representation of the binary data.
      */
    val SixteenBytesIdentifier = new Factory {
      override def generate(): Identifier = {
        val data = ByteBuffer.wrap(new Array[Byte](16))
        val highLong = ThreadLocalRandom.current().nextLong()
        val lowLong = ThreadLocalRandom.current().nextLong()
        data.putLong(highLong)
        data.putLong(lowLong)

        Identifier(HexCodec.toLowerHex(highLong) + HexCodec.toLowerHex(lowLong), data.array())
      }

      override def from(string: String): Identifier =  Try {
        val highPart = HexCodec.lowerHexToUnsignedLong(string.substring(0, 16))
        val lowPart = HexCodec.lowerHexToUnsignedLong(string.substring(16, 32))
        val data = ByteBuffer.allocate(16)
        data.putLong(highPart)
        data.putLong(lowPart)

        Identifier(string, data.array())
      } getOrElse(Empty)

      override def from(bytes: Array[Byte]): Identifier = Try {
        val buffer = ByteBuffer.wrap(bytes)
        val highLong = buffer.getLong
        val lowLong = buffer.getLong

        Identifier(HexCodec.toLowerHex(highLong) + HexCodec.toLowerHex(lowLong), bytes)
      } getOrElse(Empty)
    }

  }
}
