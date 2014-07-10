タイムシフトロガー
====================

TimeShift-loggerは、本番運用時にログが出てなくて困るあの現象を解決するためのソリューションです。
重大なエラーが発生した時には、ログを詳細に出力しておきたいが、でも本番環境でログレベルを常時DEBUGしておくわけにはいかない…

そんなときでもタイムシフトロガーがあれば大丈夫！
エラーが発生したひとのログをある一定時間遡って詳細なレベルで取得できます。


## しかけ

log4jの設定にタイムシフトロガー用のAppenderを追加します。これはSyslogAppenderによってログサーバに投げつけることになります。
ログサーバは、一定時間分だけログを保管しておいて、しきい値を超えた場合にだけ、そこに溜め込んだログを出力します。

### セットアップ

log4j.xmlに以下のAppenderを追加してください。

```xml
<appender name="timeshift" class="org.apache.log4j.net.SyslogAppender">
    <param name="SyslogHost" value="logserver:8888"/>
    <param name="Facility" value="USER"/>
    <param name="Threshold" value="DEBUG"/>
    <layout class="org.apache.log4j.PatternLayout">
        <param name="ConversionPattern" value="%d{ISO8601}\t%p\t%c\t%x\t%m">
    </layout>
</appender>
```

そして、タイムシフトログサーバを起動します。

```
% lein run
```
