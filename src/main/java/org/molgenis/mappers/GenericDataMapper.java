package org.molgenis.mappers;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import org.apache.camel.Exchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.molgenis.utils.FileCreator;
import org.springframework.stereotype.Component;

@Component
public class GenericDataMapper {

  private final AlissaMapper alissaMapper;
  private final LumcMapper lumcMapper;
  private final RadboudMumcMapper radboudMumcMapper;

  private static Log log = LogFactory.getLog(FileCreator.class);

  private enum LabType {
    lumc,
    radboud,
    alissa
  }

  public GenericDataMapper(AlissaMapper alissaMapper, LumcMapper lumcMapper,
      RadboudMumcMapper radboudMumcMapper) {
    this.alissaMapper = alissaMapper;
    this.lumcMapper = lumcMapper;
    this.radboudMumcMapper = radboudMumcMapper;
  }

  private static Boolean isTypeHeader(Set<String> headers, String typeHeader) {
    return headers.containsAll(Arrays.asList(typeHeader.split("\t")));
  }

  static String getType(Set<String> headers) throws DataFormatException {
    if (isTypeHeader(headers,
        LumcMapper.LUMC_HEADERS
            .replace("hgvs_normalized", "gDNA_normalized"))) {
      return LabType.lumc.name();
    } else if (isTypeHeader(headers, RadboudMumcMapper.RADBOUD_HEADERS)) {
      return LabType.radboud.name();
    } else if (isTypeHeader(headers,
        AlissaMapper.ALISSA_HEADERS
            .replace("_orig", ""))) {
      return LabType.alissa.name();
    } else {
      throw new DataFormatException(
          "Lab type not recognized, check headers with headers of alissa, radboud, and lumc");
    }
  }

  public void mapData(Exchange exchange) {
    Map<String, Object> body = (Map<String, Object>) exchange.getIn().getBody();
    Set<String> headers = body.keySet();
    try {
      String labType = getType(headers);
      exchange.getIn().getHeaders().put("labType", labType);

      if (labType.equals(LabType.lumc.name())) {
        lumcMapper.mapData(body);
      } else if (labType.equals(LabType.radboud.name())) {
        radboudMumcMapper.mapData(body);
      } else {
        alissaMapper.mapData(body);
      }
    } catch (DataFormatException ex) {
      log.error(ex);
    }
  }
}
