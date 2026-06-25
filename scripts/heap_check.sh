# 1. Find your PID
jps -l

# 2. Monitor GC and Heap sections every 1 second
jstat -gc <PID> 1000

# 3. Take a heap dump for analysis later
jmap -dump:live,format=b,file=heapdump.hprof <PID>
