package net.manub.embeddedkafka.streams

import net.manub.embeddedkafka.avro.schemaregistry._
import net.manub.embeddedkafka.avro.schemaregistry.Codecs._
import net.manub.embeddedkafka.Codecs._
import net.manub.embeddedkafka.ConsumerExtensions._
import net.manub.embeddedkafka.{EmbeddedKafkaConfig, TestAvroClass}
import org.apache.kafka.common.serialization.{Serde, Serdes}
import org.apache.kafka.streams.kstream.{KStream, Produced}
import org.apache.kafka.streams.{Consumed, StreamsBuilder}
import org.scalatest.{Matchers, WordSpec}

class ExampleKafkaStreamsSchemaRegistrySpec
    extends WordSpec
    with Matchers
    with EmbeddedKafkaStreamsAllInOne {

  implicit val config: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig(kafkaPort = 7000,
                        zooKeeperPort = 7001,
                        schemaRegistryPort = Some(7002))

  val (inTopic, outTopic) = ("in", "out")

  val stringSerde: Serde[String] = Serdes.String()
  val avroSerde: Serde[TestAvroClass] = serdeFrom[TestAvroClass]

  "A Kafka streams test using Schema Registry" should {
    "be easy to run with streams and consumer lifecycle management" in {
      val streamBuilder = new StreamsBuilder
      val stream: KStream[String, TestAvroClass] =
        streamBuilder.stream(inTopic, Consumed.`with`(stringSerde, avroSerde))

      stream.to(outTopic, Produced.`with`(stringSerde, avroSerde))

      runStreams(Seq(inTopic, outTopic), streamBuilder.build()) {
        publishToKafka(inTopic, "hello", TestAvroClass("world"))
        publishToKafka(inTopic, "foo", TestAvroClass("bar"))
        publishToKafka(inTopic, "baz", TestAvroClass("yaz"))
        withConsumer[String, TestAvroClass, Unit] { consumer =>
          val consumedMessages: Stream[(String, TestAvroClass)] =
            consumer.consumeLazily(outTopic)
          consumedMessages.take(2) should be(
            Seq("hello" -> TestAvroClass("world"),
                "foo" -> TestAvroClass("bar")))
          consumedMessages.drop(2).head should be("baz" -> TestAvroClass("yaz"))
        }
      }
    }

    "allow support creating custom consumers" in {
      val streamBuilder = new StreamsBuilder
      val stream: KStream[String, TestAvroClass] =
        streamBuilder.stream(inTopic, Consumed.`with`(stringSerde, avroSerde))

      stream.to(outTopic, Produced.`with`(stringSerde, avroSerde))

      runStreams(Seq(inTopic, outTopic), streamBuilder.build()) {
        publishToKafka(inTopic, "hello", TestAvroClass("world"))
        publishToKafka(inTopic, "foo", TestAvroClass("bar"))
        val consumer = newConsumer[String, TestAvroClass]()
        consumer
          .consumeLazily[(String, TestAvroClass)](outTopic)
          .take(2) should be(
          Seq("hello" -> TestAvroClass("world"), "foo" -> TestAvroClass("bar")))
        consumer.close()
      }
    }
  }
}