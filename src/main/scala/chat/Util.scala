package chat

import zio.ZIO
import zio.http.Body
import zio.json.*
import zio.schema.Schema

object Util:

  extension (body: Body)

    def as[A](using Schema[A]): ZIO[Any, Throwable, A] =
      body.asString
        .map(_.fromJson[A](using zio.schema.codec.JsonCodec.jsonDecoder(Schema[A])))
        .right
        .mapError(_.left.map(err => new Exception(err)).merge)
