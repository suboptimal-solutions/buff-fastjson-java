package io.suboptimal.buffjson;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.util.JsonFormat;
import io.suboptimal.buffjson.proto.SimpleMessage;
import io.suboptimal.buffjson.proto.Status;
import io.suboptimal.buffjson.proto.ComplexMessage;
import io.suboptimal.buffjson.proto.Address;
import io.suboptimal.buffjson.proto.Tag;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

class BuffJSONTest {

    private static final JsonFormat.Printer REFERENCE_PRINTER =
            JsonFormat.printer().omittingInsignificantWhitespace();

    @Test
    void encodeSimpleMessage() throws Exception {
        SimpleMessage message = SimpleMessage.newBuilder()
                .setName("test")
                .setId(42)
                .setTimestampMillis(1234567890L)
                .setScore(3.14)
                .setActive(true)
                .setStatus(Status.STATUS_ACTIVE)
                .build();

        String actual = BuffJSON.encode(message);
        String expected = REFERENCE_PRINTER.print(message);

        assertNotNull(actual);
        assertEquals(expected, actual);
    }

    @Test
    void encodeDefaultMessage() throws Exception {
        SimpleMessage message = SimpleMessage.getDefaultInstance();
        String actual = BuffJSON.encode(message);
        assertEquals("{}", actual);
    }

    @Test
    void encodeComplexMessage() throws Exception {
        Timestamp now = Timestamp.newBuilder()
                .setSeconds(1711627200L)
                .setNanos(0)
                .build();

        Address addr = Address.newBuilder()
                .setStreet("123 Main St")
                .setCity("Springfield")
                .setState("IL")
                .setZipCode("62704")
                .setCountry("US")
                .build();

        ComplexMessage message = ComplexMessage.newBuilder()
                .setId("msg-001")
                .setName("complex-test")
                .setVersion(1)
                .setPrimaryAddress(addr)
                .addTagsList("java")
                .addTagsList("protobuf")
                .addAddresses(addr)
                .addTags(Tag.newBuilder().setKey("env").setValue("prod").build())
                .putMetadata("key", "value")
                .setEmail("user@example.com")
                .setPayload(ByteString.copyFromUtf8("binary-data"))
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setStatus(Status.STATUS_ACTIVE)
                .build();

        String actual = BuffJSON.encode(message);
        String expected = REFERENCE_PRINTER.print(message);

        assertNotNull(actual);
        assertEquals(expected, actual);
    }
}
