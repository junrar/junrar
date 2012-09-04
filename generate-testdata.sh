
mode=$1
adstepping=$2
t1stepping=$3
t2stepping=$4
indir=$5
outdir=$6
par1=1
par2=1
path=$PWD


function start {
case "$1" in
	a|d)
		.
			while [ $par1 -le 31 ]
			do
			rar a -ep1 -mc$par1$1+ $path/$outdir/$i/$i-mc$par1$1+.rar $path/$indir/$i
			let par1+=adstepping
			done
		par1=1
	;;
	t)
			let par1=2
			while [ $par1 -le 63 ]
			do
				while [ $par2 -le 128 ]
				do
					rar a -ep1 -mc$par1:$par2$1+ $path/$outdir/$i/$i-mc$par1.$par2$1+.rar $path/$indir/$i
					let par2+=$t2stepping
				done
			let par1+=$t1stepping
			let par2=1
			done
		par1=1
	;;
	c|e|i)

			rar a -ep1 -mc$1+ $path/$outdir/$i/$i-mc$1+.rar $path/$indir/$i
			par1=1
	;;
	n)

			rar a -ep1 -mca- -mcd- -mct- -mcc- -mce- -mci-  $path/$outdir/$i/$i-n.rar $path/$indir/$i
	;;
	esac
}


if [ "$mode" != "" ] && [ "$adstepping" != "" ]  &&[ "$t1stepping" != "" ] && [ "$t2stepping" != "" ] && [ "$indir" != "" ] && [ "$outdir" != "" ];
then
	# create output dir
	mkdir -m 0777 $outdir

	cd $indir

	for i in $(ls *)
	do
		mkdir -m 0777 $path/$outdir/$i
		if [ "$1" == "all" ];then
			start a
			start d
			start c
			start e
			start i
			start t
			start n
		else
			start "$mode"
		fi
	done
else
	
	echo "usage: ./generate-testdata.sh [ a | d | c  | e | i | t | n | all ] [ adstepping ] [ t1stepping]  [ t2stepping]  [ indir ] [ outdir ]"
	echo .......................................
	exit 0
fi



