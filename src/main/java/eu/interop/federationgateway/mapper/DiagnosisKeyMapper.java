/*-
 * ---license-start
 * EU-Federation-Gateway-Service / efgs-federation-gateway
 * ---
 * Copyright (C) 2020 T-Systems International GmbH and all other contributors
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package eu.interop.federationgateway.mapper;

import com.google.protobuf.ByteString;
import eu.interop.federationgateway.batchsigning.BatchSignatureUtils;
import eu.interop.federationgateway.entity.DiagnosisKeyEntity;
import eu.interop.federationgateway.entity.DiagnosisKeyPayload;
import eu.interop.federationgateway.entity.FormatInformation;
import eu.interop.federationgateway.entity.UploaderInformation;
import eu.interop.federationgateway.model.EfgsProto;
import eu.interop.federationgateway.utils.SemVerUtils;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.springframework.http.MediaType;

@Slf4j
@Mapper(componentModel = "spring")
public abstract class DiagnosisKeyMapper {

  /**
   * Converts {@link EfgsProto.DiagnosisKey} entity to a JPA {@link DiagnosisKeyEntity}.
   *
   * @param proto          the protobuf entity
   * @param uploadBatchTag the uploadBatchTag which will be set for all entities
   * @return the converted JPA entity
   */
  public DiagnosisKeyEntity protoToEntity(
    EfgsProto.DiagnosisKey proto,
    String uploadBatchTag,
    String uploadBatchSignature,
    String certificateThumbprint,
    String signingThumbprint,
    String certificateCountry,
    MediaType format
  ) {
    DiagnosisKeyEntity entity = new DiagnosisKeyEntity();

    try {
      SemVerUtils.SemVer semVer = SemVerUtils.parseSemVer(Objects.requireNonNull(format.getParameter("version")));
      entity.setFormat(new FormatInformation(
        semVer.getMajor(),
        semVer.getMinor()
      ));
    } catch (SemVerUtils.SemVerParsingException e) {
      log.error("Could not parse semver from content type!");
    }

    entity.setPayload(new DiagnosisKeyPayload(
      proto.getKeyData().toByteArray(),
      proto.getRollingStartIntervalNumber(),
      proto.getRollingPeriod(),
      proto.getTransmissionRiskLevel(),
      String.join(",", proto.getVisitedCountriesList()),
      proto.getOrigin(),
      mapReportType(proto.getReportType()),
      proto.getDaysSinceOnsetOfSymptoms()
    ));

    entity.setUploader(new UploaderInformation(
      uploadBatchTag,
      uploadBatchSignature,
      certificateThumbprint,
      signingThumbprint,
      certificateCountry
    ));

    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(
        BatchSignatureUtils.generateBytesToVerify(proto)
      );

      entity.setPayloadHash(new BigInteger(1, hash).toString(16));
    } catch (NoSuchAlgorithmException e) {
      entity.setPayloadHash(null);
    }

    return entity;
  }

  /**
   * Converts a set of Diagnosis Keys Protobuf messages to a JPA entity.
   *
   * @param proto          set of protobuf messages
   * @param uploadBatchTag the batch tag for all messages
   * @return set of converted entities
   */
  public List<DiagnosisKeyEntity> protoToEntity(
    List<EfgsProto.DiagnosisKey> proto,
    String uploadBatchTag,
    String uploadBatchSignature,
    String certificateThumbprint,
    String signingThumbprint,
    String certificateCountry,
    MediaType format
  ) {
    return proto.stream()
      .map(p -> protoToEntity(
        p,
        uploadBatchTag,
        uploadBatchSignature,
        certificateThumbprint,
        signingThumbprint,
        certificateCountry,
        format
      ))
      .collect(Collectors.toList());
  }

  /**
   * Converts JPA DiagnosisKeyEntity to an Efgs Protobuf entity.
   * TODO: Use some fancy built-in features from mapstruct.
   *
   * @param entity the JPA entity
   * @return the converted protobuf entity
   */
  public EfgsProto.DiagnosisKey entityToProto(DiagnosisKeyEntity entity) {
    return EfgsProto.DiagnosisKey.newBuilder()
      .setKeyData(byteArrayToByteString(entity.getPayload().getKeyData()))
      .setRollingStartIntervalNumber(entity.getPayload().getRollingStartIntervalNumber())
      .setRollingPeriod(entity.getPayload().getRollingPeriod())
      .setTransmissionRiskLevel(entity.getPayload().getTransmissionRiskLevel())
      .addAllVisitedCountries(Arrays.asList(entity.getPayload().getVisitedCountries().split(",")))
      .setOrigin(entity.getPayload().getOrigin())
      .setReportType(mapReportType(entity.getPayload().getReportType()))
      .setDaysSinceOnsetOfSymptoms(entity.getPayload().getDaysSinceOnsetOfSymptoms())
      .build();
  }

  public abstract ArrayList<EfgsProto.DiagnosisKey> entityToProto(List<DiagnosisKeyEntity> entity);

  public byte[] byteStringToByteArray(ByteString byteString) {
    return byteString.toByteArray();
  }

  public ByteString byteArrayToByteString(byte[] byteArray) {
    return ByteString.copyFrom(byteArray);
  }

  public abstract EfgsProto.ReportType mapReportType(
    DiagnosisKeyPayload.ReportType verificationType
  );

  @ValueMappings({
    @ValueMapping(source = "UNRECOGNIZED", target = MappingConstants.NULL)
  })
  public abstract DiagnosisKeyPayload.ReportType mapReportType(
    EfgsProto.ReportType reportType
  );
}
