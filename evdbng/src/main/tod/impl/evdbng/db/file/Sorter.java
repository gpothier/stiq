package tod.impl.evdbng.db.file;

/**
 * A copy of Java's Arrays sorting algorithm, but allows to override the swap
 * method so that matched arrays (for instance) can be sorted efficiently.
 * 
 * @author gpothier
 * 
 */
public abstract class Sorter
{
	public void sort(int[] a)
	{
		sort1(a, 0, a.length);
	}

	public void sort(long[] a)
	{
		sort1(a, 0, a.length);
	}

	/**
	 * Sorts the specified sub-array of longs into ascending order.
	 */
	private void sort1(long x[], int off, int len)
	{
		// Insertion sort on smallest arrays
		if (len < 7)
		{
			for (int i = off; i < len + off; i++)
				for (int j = i; j > off && x[j - 1] > x[j]; j--)
					swap0(x, j, j - 1);
			return;
		}

		// Choose a partition element, v
		int m = off + (len >> 1); // Small arrays, middle element
		if (len > 7)
		{
			int l = off;
			int n = off + len - 1;
			if (len > 40)
			{ // Big arrays, pseudomedian of 9
				int s = len / 8;
				l = med3(x, l, l + s, l + 2 * s);
				m = med3(x, m - s, m, m + s);
				n = med3(x, n - 2 * s, n - s, n);
			}
			m = med3(x, l, m, n); // Mid-size, med of 3
		}
		long v = x[m];

		// Establish Invariant: v* (<v)* (>v)* v*
		int a = off, b = a, c = off + len - 1, d = c;
		while (true)
		{
			while (b <= c && x[b] <= v)
			{
				if (x[b] == v) swap0(x, a++, b);
				b++;
			}
			while (c >= b && x[c] >= v)
			{
				if (x[c] == v) swap0(x, c, d--);
				c--;
			}
			if (b > c) break;
			swap0(x, b++, c--);
		}

		// Swap partition elements back to middle
		int s, n = off + len;
		s = Math.min(a - off, b - a);
		vecswap(x, off, b - s, s);
		s = Math.min(d - c, n - d - 1);
		vecswap(x, b, n - s, s);

		// Recursively sort non-partition-elements
		if ((s = b - a) > 1) sort1(x, off, s);
		if ((s = d - c) > 1) sort1(x, n - s, s);
	}

	/**
	 * Swaps x[a] with x[b].
	 */
	private void swap0(long x[], int a, int b)
	{
		swap(x, a, b);
		swap(a, b);
	}

	protected void swap(long x[], int a, int b)
	{
		long t = x[a];
		x[a] = x[b];
		x[b] = t;
	}

	/**
	 * Swaps x[a .. (a+n-1)] with x[b .. (b+n-1)].
	 */
	private void vecswap(long x[], int a, int b, int n)
	{
		for (int i = 0; i < n; i++, a++, b++)
			swap0(x, a, b);
	}

	/**
	 * Returns the index of the median of the three indexed longs.
	 */
	private static int med3(long x[], int a, int b, int c)
	{
		return (x[a] < x[b] ? (x[b] < x[c] ? b : x[a] < x[c] ? c : a) : (x[b] > x[c] ? b : x[a] > x[c] ? c : a));
	}

	/**
	 * Sorts the specified sub-array of integers into ascending order.
	 */
	private void sort1(int x[], int off, int len)
	{
		// Insertion sort on smallest arrays
		if (len < 7)
		{
			for (int i = off; i < len + off; i++)
				for (int j = i; j > off && x[j - 1] > x[j]; j--)
					swap0(x, j, j - 1);
			return;
		}

		// Choose a partition element, v
		int m = off + (len >> 1); // Small arrays, middle element
		if (len > 7)
		{
			int l = off;
			int n = off + len - 1;
			if (len > 40)
			{ // Big arrays, pseudomedian of 9
				int s = len / 8;
				l = med3(x, l, l + s, l + 2 * s);
				m = med3(x, m - s, m, m + s);
				n = med3(x, n - 2 * s, n - s, n);
			}
			m = med3(x, l, m, n); // Mid-size, med of 3
		}
		int v = x[m];

		// Establish Invariant: v* (<v)* (>v)* v*
		int a = off, b = a, c = off + len - 1, d = c;
		while (true)
		{
			while (b <= c && x[b] <= v)
			{
				if (x[b] == v) swap0(x, a++, b);
				b++;
			}
			while (c >= b && x[c] >= v)
			{
				if (x[c] == v) swap0(x, c, d--);
				c--;
			}
			if (b > c) break;
			swap0(x, b++, c--);
		}

		// Swap partition elements back to middle
		int s, n = off + len;
		s = Math.min(a - off, b - a);
		vecswap(x, off, b - s, s);
		s = Math.min(d - c, n - d - 1);
		vecswap(x, b, n - s, s);

		// Recursively sort non-partition-elements
		if ((s = b - a) > 1) sort1(x, off, s);
		if ((s = d - c) > 1) sort1(x, n - s, s);
	}

	/**
	 * Swaps x[a] with x[b].
	 */
	private void swap0(int x[], int a, int b)
	{
		swap(x, a, b);
		swap(a, b);
	}

	protected void swap(int x[], int a, int b)
	{
		int t = x[a];
		x[a] = x[b];
		x[b] = t;
	}

	/**
	 * Swaps x[a .. (a+n-1)] with x[b .. (b+n-1)].
	 */
	private void vecswap(int x[], int a, int b, int n)
	{
		for (int i = 0; i < n; i++, a++, b++)
			swap0(x, a, b);
	}

	/**
	 * Returns the index of the median of the three indexed integers.
	 */
	private static int med3(int x[], int a, int b, int c)
	{
		return (x[a] < x[b] ? (x[b] < x[c] ? b : x[a] < x[c] ? c : a) : (x[b] > x[c] ? b : x[a] > x[c] ? c : a));
	}

	/**
	 * Swaps the items at positions a and b.
	 */
	protected abstract void swap(int a, int b);
}