package io.suboptimal.buffjson.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.suboptimal.buffjson.jackson.proto.JacksonNestedMessage;
import io.suboptimal.buffjson.jackson.proto.JacksonTestScalars;
import io.suboptimal.buffjson.jackson.proto.JacksonTestWellKnown;

/**
 * Integration tests verifying that protobuf messages work alongside regular
 * POJOs and Java records in Jackson serialization — the primary use case for
 * the Jackson module.
 *
 * <p>
 * This validates the real-world scenario where a service returns a response DTO
 * (record or POJO) containing protobuf message fields, and the entire object
 * graph is serialized/deserialized through Jackson's ObjectMapper.
 *
 * <p>
 * Test groups:
 * <ul>
 * <li><b>RecordWithProtoField</b> — Java records containing proto fields, proto
 * lists, and mixed types
 * <li><b>PojoWithProtoField</b> — traditional POJO class with proto field
 * <li><b>TokenParserAndTreeModel</b> — Jackson tree model interop (readTree,
 * treeToValue, valueToTree workaround, generic list)
 * <li><b>WellKnownTypesInPojo</b> — records with WKT fields (Timestamp as RFC
 * 3339 string inside a record)
 * <li><b>EdgeCases</b> — null proto, empty (default) proto, bytes field
 * </ul>
 */
class JacksonPojoIntegrationTest {

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new ProtobufJacksonModule());

	// --- Test DTOs: records and POJOs containing protobuf message fields ---

	/** Typical API response with status, code, and a proto payload. */
	record ApiResponse(String status, int code, JacksonTestScalars data) {
	}

	/** Response containing a list of proto messages. */
	record ApiListResponse(String status, List<JacksonNestedMessage> items) {
	}

	/** Record mixing proto fields with regular Java types. */
	record MixedRecord(String name, JacksonNestedMessage proto, List<String> tags) {
	}

	/** Record with a well-known type field (Timestamp inside the proto). */
	record TimestampHolder(String event, JacksonTestWellKnown details) {
	}

	/** Traditional POJO (not record) with a proto field. */
	static class PojoWrapper {
		public String type;
		public JacksonTestScalars payload;

		// Default constructor required by Jackson for deserialization
		public PojoWrapper() {
		}

		public PojoWrapper(String type, JacksonTestScalars payload) {
			this.type = type;
			this.payload = payload;
		}
	}

	/**
	 * Verifies records containing proto fields serialize and roundtrip correctly.
	 */
	@Nested
	class RecordWithProtoField {

		@Test
		void recordContainingProtoMessage() throws Exception {
			var proto = JacksonTestScalars.newBuilder().setInt32Val(42).setStringVal("hello").build();
			var response = new ApiResponse("ok", 200, proto);

			String json = MAPPER.writeValueAsString(response);

			// Verify the JSON structure contains both POJO and proto fields
			JsonNode tree = MAPPER.readTree(json);
			assertEquals("ok", tree.get("status").asText());
			assertEquals(200, tree.get("code").asInt());
			assertEquals(42, tree.get("data").get("int32Val").asInt());
			assertEquals("hello", tree.get("data").get("stringVal").asText());

			// Roundtrip
			ApiResponse decoded = MAPPER.readValue(json, ApiResponse.class);
			assertEquals(response, decoded);
		}

		@Test
		void recordContainingProtoList() throws Exception {
			var items = List.of(JacksonNestedMessage.newBuilder().setId(1).setName("a").build(),
					JacksonNestedMessage.newBuilder().setId(2).setName("b").build());
			var response = new ApiListResponse("ok", items);

			String json = MAPPER.writeValueAsString(response);
			ApiListResponse decoded = MAPPER.readValue(json, ApiListResponse.class);
			assertEquals(response, decoded);
		}

		@Test
		void mixedRecordWithTags() throws Exception {
			var proto = JacksonNestedMessage.newBuilder().setId(5).setName("five").build();
			var mixed = new MixedRecord("test", proto, List.of("tag1", "tag2"));

			String json = MAPPER.writeValueAsString(mixed);
			MixedRecord decoded = MAPPER.readValue(json, MixedRecord.class);
			assertEquals(mixed, decoded);
		}
	}

	/** Verifies traditional POJOs (non-record) with proto fields. */
	@Nested
	class PojoWithProtoField {

		@Test
		void classContainingProtoMessage() throws Exception {
			var proto = JacksonTestScalars.newBuilder().setInt32Val(99).setBoolVal(true).build();
			var wrapper = new PojoWrapper("scalars", proto);

			String json = MAPPER.writeValueAsString(wrapper);
			PojoWrapper decoded = MAPPER.readValue(json, PojoWrapper.class);
			assertEquals(wrapper.type, decoded.type);
			assertEquals(wrapper.payload, decoded.payload);
		}
	}

	/**
	 * Verifies Jackson tree model interop: readTree→treeToValue, proto→tree
	 * conversion, and proto messages inside generic collections.
	 */
	@Nested
	class TokenParserAndTreeModel {

		@Test
		void readProtoFromJsonNode() throws Exception {
			String json = """
					{"int32Val":42,"stringVal":"from-tree"}""";
			JsonNode tree = MAPPER.readTree(json);

			// Convert tree to proto via treeToValue
			JacksonTestScalars proto = MAPPER.treeToValue(tree, JacksonTestScalars.class);
			assertEquals(42, proto.getInt32Val());
			assertEquals("from-tree", proto.getStringVal());
		}

		@Test
		void valueToTree() throws Exception {
			var proto = JacksonTestScalars.newBuilder().setInt32Val(7).setDoubleVal(3.14).build();

			// valueToTree uses TokenBuffer internally — the serializer detects this
			// and copies structured tokens instead of using writeRawValue
			JsonNode tree = MAPPER.valueToTree(proto);
			assertTrue(tree.isObject(), "valueToTree should produce an object node");
			assertEquals(7, tree.get("int32Val").asInt());
			assertEquals(3.14, tree.get("doubleVal").asDouble(), 0.001);
		}

		@Test
		void convertValue() throws Exception {
			// convertValue uses TokenBuffer under the hood (serialize → deserialize)
			var proto = JacksonTestScalars.newBuilder().setInt32Val(42).setStringVal("convert").build();

			// Convert proto → JsonNode → proto roundtrip via convertValue
			JsonNode node = MAPPER.convertValue(proto, JsonNode.class);
			assertTrue(node.isObject());
			assertEquals(42, node.get("int32Val").asInt());
			assertEquals("convert", node.get("stringVal").asText());

			JacksonTestScalars back = MAPPER.convertValue(node, JacksonTestScalars.class);
			assertEquals(proto, back);
		}

		@Test
		void protoInsideGenericList() throws Exception {
			var list = List.of(JacksonNestedMessage.newBuilder().setId(1).setName("one").build(),
					JacksonNestedMessage.newBuilder().setId(2).setName("two").build());

			String json = MAPPER.writeValueAsString(list);

			// Read back as list of JsonNode and verify structure
			List<JsonNode> nodes = MAPPER.readValue(json, new TypeReference<List<JsonNode>>() {
			});
			assertEquals(2, nodes.size());
			assertEquals(1, nodes.get(0).get("id").asInt());
			assertEquals("two", nodes.get(1).get("name").asText());
		}
	}

	/** Verifies WKTs (Timestamp, etc.) render correctly inside record fields. */
	@Nested
	class WellKnownTypesInPojo {

		@Test
		void recordWithWkt() throws Exception {
			var wkt = JacksonTestWellKnown.newBuilder().setTimestamp(Timestamp.newBuilder().setSeconds(1704067200))
					.build();
			var holder = new TimestampHolder("login", wkt);

			String json = MAPPER.writeValueAsString(holder);
			JsonNode tree = MAPPER.readTree(json);
			assertEquals("login", tree.get("event").asText());
			// Timestamp should be RFC3339 string
			assertEquals("2024-01-01T00:00:00Z", tree.get("details").get("timestamp").asText());

			TimestampHolder decoded = MAPPER.readValue(json, TimestampHolder.class);
			assertEquals(holder, decoded);
		}
	}

	/** Edge cases: null proto, empty proto (default instance), bytes field. */
	@Nested
	class EdgeCases {

		@Test
		void nullProtoInRecord() throws Exception {
			var response = new ApiResponse("empty", 204, null);
			String json = MAPPER.writeValueAsString(response);

			JsonNode tree = MAPPER.readTree(json);
			assertEquals(true, tree.get("data").isNull());

			ApiResponse decoded = MAPPER.readValue(json, ApiResponse.class);
			assertEquals(response, decoded);
		}

		@Test
		void emptyProtoInRecord() throws Exception {
			var response = new ApiResponse("default", 200, JacksonTestScalars.getDefaultInstance());
			String json = MAPPER.writeValueAsString(response);
			ApiResponse decoded = MAPPER.readValue(json, ApiResponse.class);
			assertEquals(response, decoded);
		}

		@Test
		void protoWithBytesInRecord() throws Exception {
			var proto = JacksonTestScalars.newBuilder().setBytesVal(ByteString.copyFromUtf8("binary")).build();
			var response = new ApiResponse("bytes", 200, proto);
			String json = MAPPER.writeValueAsString(response);
			ApiResponse decoded = MAPPER.readValue(json, ApiResponse.class);
			assertEquals(response, decoded);
		}

		@Test
		void malformedJsonThrowsIOException() {
			// BuffJson.decode() throws a RuntimeException (fastjson2 JSONException)
			// on malformed input. The deserializer wraps it as IOException so Jackson's
			// error handling can intercept it.
			String malformed = "{\"int32Val\": \"not-a-number\"}";
			var ex = assertThrows(Exception.class, () -> MAPPER.readValue(malformed, JacksonTestScalars.class));
			// Jackson wraps IOException in JsonMappingException — verify the cause chain
			// contains our wrapping IOException with the "Failed to decode" message
			Throwable cause = ex;
			boolean foundDecodeMessage = false;
			while (cause != null) {
				if (cause.getMessage() != null && cause.getMessage().contains("Failed to decode protobuf")) {
					foundDecodeMessage = true;
					break;
				}
				cause = cause.getCause();
			}
			assertTrue(foundDecodeMessage, "Exception chain should contain 'Failed to decode protobuf' message");
		}
	}
}
